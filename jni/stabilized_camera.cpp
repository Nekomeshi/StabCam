#include <jni.h>
#include <time.h>
#include <math.h>
#include <vector>

#define LOG_TAG "StabilizedCamera"
#include "log.h"
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/features2d/features2d.hpp>
#include "opencv2/video/video.hpp"
#include "com_nekomeshi312_stabcam_StabCameraViewFragment.h"

#define DEBUG
#undef DEBUG

using namespace std;

#define abs(a) ((a) > 0 ? (a):-(a))

class CVRAverage{
public:
	CVRAverage(){
		m_Sum = 0.0;
		m_VRValues.clear();
	}
	void pushVRValue(float vrValue){
		m_VRValues.push_back(vrValue);
		m_Sum += vrValue;
		if(m_VRValues.size() > AVERAGE_COUNT){
			m_Sum -= m_VRValues.at(0);
			m_VRValues.erase(m_VRValues.begin());
		}
	}

	float getAverage(void){
		const int sz = m_VRValues.size();
		return sz == 0 ? 0.0 : m_Sum/(float)sz;
	}

private:
	static const int AVERAGE_COUNT = 10;
	vector<float> m_VRValues;
	float m_Sum;
};

#define USE_POC//位相限定相関法によるシフト検出
#undef USE_POC//LK法によるシフト検出

#define FEATURE_RECALC_NUMBER 10
#define SCALING_VAL 2
int gWidth;
int gHeight;
int gWidthScaling;
int gHeightScaling;

#define CENTERING_SCALE 20
int gCenteringSizeW;
int gCenteringSizeH;

cv::Mat gGrayPrev;
cv::Mat gGrayCur;
cv::Mat gRGBACur;
cv::Mat gAffine;

#ifdef USE_POC
cv::Mat gHann;
#endif

CVRAverage gVRAveW;
CVRAverage gVRAveH;

/*
 * Class:     com_nekomeshi312_stabcam_StabCameraViewFragment
 * Method:    initStabilize
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_com_nekomeshi312_stabcam_StabCameraViewFragment_initStabilize
(JNIEnv *jenv, jobject jobj, jint w, jint h)
{
	LOGI("initStabilize");
	gWidth = w;
	gHeight = h;
	gWidthScaling = w/SCALING_VAL;
	gHeightScaling = h/SCALING_VAL;

	gCenteringSizeW = w/CENTERING_SCALE;
	gCenteringSizeH = h/CENTERING_SCALE;

#ifdef USE_POC
	gGrayPrev = cv::Mat(cv::Size(gWidthScaling, gHeightScaling), CV_64FC1);
	gGrayCur = cv::Mat(cv::Size(gWidthScaling, gHeightScaling), CV_64FC1);
	gHann = cv::Mat(cv::Size(gWidthScaling, gHeightScaling), CV_64FC1);
#else
	gGrayPrev = cv::Mat(cv::Size(gWidthScaling, gHeightScaling), CV_8UC1);
	gGrayCur = cv::Mat(cv::Size(gWidthScaling, gHeightScaling), CV_8UC1);
#endif

	gRGBACur = cv::Mat(cv::Size(gWidth, gHeight), CV_8UC4);
	gAffine = (cv::Mat_<float>(2, 3) << 1.0, 0.0, 0.0, 0.0, 1.0, 0.0);
}

/*
 * Class:     com_nekomeshi312_stabcam_StabCameraViewFragment
 * Method:    stopStabilize
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_nekomeshi312_stabcam_StabCameraViewFragment_stopStabilize
(JNIEnv *jenv, jobject jobj)
{
	LOGI("stopStabilize");
	gGrayPrev.release();
	gGrayCur.release();
	gRGBACur.release();
	gAffine.release();
#ifdef USE_POC
	gHann.release();
#endif
}
#ifdef DEBUG
double lastTime = 0.0;
#define logTimeDiff(msg) \
{\
	struct timespec tp;\
	clock_gettime( CLOCK_REALTIME, &tp);\
	double curTime = (double)tp.tv_sec + (double)tp.tv_nsec/1000000000.0;\
	LOGD("%s Diff Time = %lf", msg, curTime - lastTime);\
	lastTime = curTime;\
}
#else
#define logTimeDiff(msg)
#endif

bool isFirst = true;
/*
 * Class:     com_nekomeshi312_stabcam_StabCameraViewFragment
 * Method:    getStabilizedImage
 * Signature: ([BJZ)V
 */
JNIEXPORT void JNICALL Java_com_nekomeshi312_stabcam_StabCameraViewFragment_getStabilizedImage
(JNIEnv *jenv, jobject jobj, jbyteArray camImg, jlong matStabilizedImg, jboolean doVR)
{
	logTimeDiff("At Java");

    jbyte *img = (jbyte *)jenv->GetByteArrayElements(camImg, 0);
    cv::Mat* pStabImg = (cv::Mat*)matStabilizedImg;
	logTimeDiff("Prepare Image0 ");
#ifdef USE_POC
    //grayのMatを準備
    cv::Mat gray(cv::Size(gWidth,gHeight), CV_8UC1, img);
    cv::resize(gray, gray, cv::Size(gWidthScaling,gHeightScaling), 0, 0, cv::INTER_NEAREST);
    gray.convertTo(gGrayCur, CV_64F);
    //RGBAのMatを準備
    cv::Mat yuv420(cv::Size(gWidth, gHeight + (gHeight >> 1)), CV_8UC1, img);
    cv::cvtColor(yuv420, gRGBACur, cv::COLOR_YUV420sp2RGBA);

    if(!isFirst){
        cv::Point2d shift = cv::phaseCorrelate(gGrayPrev, gGrayCur, gHann);
        if(shift.x > 70) goto POC_END;

		const float ddx = shift.x * SCALING_VAL;
		const float ddy = shift.y * SCALING_VAL;
#ifdef DEBUG
		LOGD("dx = %f dy =  %f", shift.x, shift.y);
#endif

		gAffine.at<float>(0, 2) -= ddx;
		gAffine.at<float>(1, 2) -= ddy;

		//平均的に大きくぶれているときは振幅を計算する基数を1に近づけて中心に寄せる速度を遅くする
		gVRAveW.pushVRValue(abs(ddx));
		gVRAveH.pushVRValue(abs(ddy));
		const float cardinalW = pow(0.94, 1.0 - 10.0*gVRAveW.getAverage()/(float)gWidth);
		const float cardinalH = pow(0.94, 1.0 - 10.0*gVRAveH.getAverage()/(float)gHeight);
#ifdef DEBUG
		LOGD("cardinalW = %lf, cardinalH = %lf, %d %d", cardinalW, cardinalH, gCenteringSizeW, gCenteringSizeH);
		LOGD("%lf, %lf", gAffine.at<float>(0, 2), gAffine.at<float>(1, 2));
#endif
		const float scaleW = pow(cardinalW, abs(gAffine.at<float>(0, 2)) / (float)gCenteringSizeW );
		const float scaleH = pow(cardinalH, abs(gAffine.at<float>(1, 2)) / (float)gCenteringSizeH );

#ifdef DEBUG
		LOGD("scaleW = %lf, scaleH = %lf", scaleW, scaleH);
#endif

		gAffine.at<float>(0, 2) *= scaleW;
		gAffine.at<float>(1, 2) *= scaleH;

		cv::warpAffine(gRGBACur,
						*pStabImg,
						gAffine,
	                    cv::Size(gWidth, gHeight),
	                    cv::INTER_NEAREST,//nearestにしないと遅い・・
	                    cv::BORDER_CONSTANT);
    }
    else{
        createHanningWindow(gHann, gGrayCur.size(), CV_64F);
        isFirst = !isFirst;
		gAffine.at<float>(0, 2) = 0.0;
		gAffine.at<float>(1, 2) = 0.0;
    }

POC_END:
	cv::Mat tmp = gGrayCur;
	gGrayCur = gGrayPrev;
	gGrayPrev = tmp;

#else

    //grayのMatを準備
    cv::Mat gray(cv::Size(gWidth,gHeight), CV_8UC1, img);
    cv::resize(gray, gGrayCur, cv::Size(gWidthScaling,gHeightScaling), 0, 0, cv::INTER_NEAREST);
	logTimeDiff("Prepare Image1 ");

    //RGBAのMatを準備
    cv::Mat yuv420(cv::Size(gWidth, gHeight + (gHeight >> 1)), CV_8UC1, img);
    cv::cvtColor(yuv420, gRGBACur, cv::COLOR_YUV420sp2RGBA);
	logTimeDiff("Prepare Image2 ");

	static std::vector<cv::Point2f>	prevPoints;

	logTimeDiff("Prepare Image3 ");

	if(!doVR){
		gRGBACur.copyTo(*pStabImg);
	}
	else{
		bool warpOK = false;
		if(prevPoints.size() > FEATURE_RECALC_NUMBER){
			std::vector<cv::Point2f>	newPoints;
			std::vector<unsigned char>  status;
			std::vector<float>       	errors;
			cv::calcOpticalFlowPyrLK(gGrayPrev,
									gGrayCur,
									prevPoints,
									newPoints,
									status,
									errors,
									cv::Size(11,11),
									3,
									cvTermCriteria (CV_TERMCRIT_ITER | CV_TERMCRIT_EPS, 20, 0.1),
									0);
			logTimeDiff("cv::calcOpticalFlowPyrLK");

			float dx = 0.0;
			float dy = 0.0;
			std::vector<cv::Point2f>	cur;
			for(int i = 0;i < newPoints.size();i++){
				if(status.at(i) == 0) continue;
				dx += (newPoints.at(i).x - prevPoints.at(i).x);
				dy += (newPoints.at(i).y - prevPoints.at(i).y);
				cur.push_back(newPoints.at(i));
			}
			const int sz = cur.size();
			if(sz > FEATURE_RECALC_NUMBER){
				const float scl = (float) SCALING_VAL / (float)sz;
				const float ddx = dx * scl;
				const float ddy = dy * scl;
#ifdef DEBUG
				LOGE("dx = %f dy =  %f, point num = %d", ddx, ddy, sz);
#endif
				gAffine.at<float>(0, 2) -= ddx;
				gAffine.at<float>(1, 2) -= ddy;

				//平均的に大きくぶれているときは振幅を計算する基数を1に近づけて中心に寄せる速度を遅くする
				gVRAveW.pushVRValue(abs(ddx));
				gVRAveH.pushVRValue(abs(ddy));
				const float cardinalW = pow(0.94, 1.0 - 10.0*gVRAveW.getAverage()/(float)gWidth);
				const float cardinalH = pow(0.94, 1.0 - 10.0*gVRAveH.getAverage()/(float)gHeight);
#ifdef DEBUG
				LOGD("cardinalW = %lf, cardinalH = %lf", cardinalW, cardinalH);
#endif
				const float scaleW = pow(cardinalW, abs(gAffine.at<float>(0, 2)) / (float)gCenteringSizeW );
				const float scaleH = pow(cardinalH, abs(gAffine.at<float>(1, 2)) / (float)gCenteringSizeH );
				gAffine.at<float>(0, 2) *= scaleW;
				gAffine.at<float>(1, 2) *= scaleH;

				cv::warpAffine(gRGBACur,
								*pStabImg,
								gAffine,
			                    cv::Size(gWidth, gHeight),
			                    cv::INTER_NEAREST,//nearestにしないと遅い・・
			                    cv::BORDER_CONSTANT);
				warpOK = true;
			}
			logTimeDiff("cv::warpAffine");

			prevPoints.clear();
			prevPoints = cur;
		}
		else{
			std::vector<cv::KeyPoint>	prevKeypoints;
			cv::GoodFeaturesToTrackDetector detector(FEATURE_RECALC_NUMBER*4, 0.1, 3);
			detector.detect(gGrayCur, prevKeypoints);

			prevPoints.clear();
			for( std::vector<cv::KeyPoint>::iterator itk = prevKeypoints.begin(); itk != prevKeypoints.end(); ++itk){
				prevPoints.push_back(itk->pt);
			}
			gAffine.at<float>(0, 2) = 0.0;
			gAffine.at<float>(1, 2) = 0.0;

			LOGD("FeaturePoint Created");

			logTimeDiff("cv::GoodFeaturesToTrackDetector");
		}

		if(!warpOK){
			gRGBACur.copyTo(*pStabImg);
		}
		cv::Mat tmp = gGrayCur;
		gGrayCur = gGrayPrev;
		gGrayPrev = tmp;
		logTimeDiff("Image Copy");
	}

#endif
	jenv->ReleaseByteArrayElements(camImg, img, 0);

	return;
}
