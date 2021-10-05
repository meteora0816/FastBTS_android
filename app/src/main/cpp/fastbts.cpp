// Write C++ code here.
//
// Do not forget to dynamically load the C++ library into your application.
//
// For instance,
//
// In MainActivity.java:
//    static {
//       System.loadLibrary("fastbts");
//    }
//
// Or, in MainActivity.kt:
//    companion object {
//      init {
//         System.loadLibrary("fastbts")
//      }
//    }

/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include <string>
#include <vector>
#include <algorithm>
#include <cmath>
#include <jni.h>
#include <android/log.h>

#define CLOCKWISE 1
double p[10005];
double porigin[10005];
double x[10005];
double y[10005];
double mi[10005];
double eps=1e-6;
int n;
double ml;
const double inf=1e18;
double up,down;
struct point{
    double x,y;
    int i;
    point(){;}
    point(double xx,double yy,double ii):x(xx),y(yy),i(ii){;}
    point operator-(const point p){
        point pp;
        pp.x=x-p.x;
        pp.y=y-p.y;
        return pp;
    }
};
double outProd(point p1,point p2){
    return p1.x*p2.y-p1.y*p2.x;
}
bool ccw(point pa,point pb,point pc){
//    __android_log_print(ANDROID_LOG_INFO, "JNI_TAG" , "ccw s %lf %lf %lf %lf", pa.x, pa.y, pb.x, pb.y);
    if(outProd(pb-pa,pc-pa)<=eps) {
        return CLOCKWISE;
    }
    return CLOCKWISE^1;
}
bool ok(double m){
    for(int i=0;i<n;i++){
        y[i]=i*i+m*p[i]-2*i;
        x[i]=i;
        mi[i]=-2*i-i*i+m*p[i]-1;
//        __android_log_print(ANDROID_LOG_INFO, "JNI_TAG" , "%lf %lf", x[i], y[i]);
    }
    std::vector<point> stk;
    stk.push_back(point(-1,-inf,-1));
    stk.push_back(point(x[0],y[0],0));
    int q=0;
    for(int i=1;i<n;i++){
        double k=2*i;
        if(q>=stk.size())q=stk.size()-1;
        while(q+1<stk.size()&&stk[q+1].y-k*stk[q+1].x>stk[q].y-k*stk[q].x&&p[i]-p[stk[q+1].i]>=ml)q++;
        while(q>0&&(stk[q-1].y-k*stk[q-1].x>stk[q].y-k*stk[q].x||p[i]-p[stk[q].i]<ml))q--;
        double b=stk[q].y-k*stk[q].x;
        if(b>=mi[i]){
            up=p[i];
            down=p[stk[q].i];
            return true;
        }
        for(int j=stk.size();j>=2&&ccw(stk[j-2],stk[j-1],point(x[i],y[i],i))!=CLOCKWISE;j--)
            stk.pop_back();
        stk.push_back(point(x[i],y[i],i));
    }
    return false;
}
double max(double a, double b) {
    return a>b?a:b;
}
extern "C" {
    JNIEXPORT jstring JNICALL
    Java_com_example_fastbts_NDKTools_stringFromJNI(JNIEnv *env, jclass clazz) {
        std::string s = "today is a good day";

        return env->NewStringUTF(s.c_str());
    }

    JNIEXPORT jdoubleArray JNICALL
    Java_com_example_fastbts_NDKTools_CIS(JNIEnv *env, jclass clazz, jlongArray javaArr, jint javaArrSize) {
        long long *speedArr = env->GetLongArrayElements(javaArr, NULL);
        int size = javaArrSize;
//        __android_log_print(ANDROID_LOG_INFO, "JNI_TAG" , "%d . %lld" , size , speedArr[1]);

        n = size+1;
        for(int i=0;i<n;i++) {
            p[i] = (double) speedArr[i];
            porigin[i] = p[i];
//            __android_log_print(ANDROID_LOG_INFO, "JNI_TAG" , "%d . %lf" , i , p[i]);
        }
        std::sort(p,p+n);
        ml=(p[n-1]-p[0])/(n-1);
        double res=0;
        double l=0,r=1e9,m;
        while(r>l+eps){
            m=(l+r)/2;
            if(ok(m))res=max(res,m),l=m;
            else r=m;
        }
        int kk=0;
        double sum=0;
        for(int i=0;i<n;i++)
            if(p[i]>=down&&p[i]<=up)
                kk++,sum+=p[i];
//		cout<<up<<'\t'<<down<<'\t'<<kk<<'\t';
//        cout<<sum/kk<<endl;
        __android_log_print(ANDROID_LOG_INFO, "result(JNI_TAG)" , "%lf %lf %lf" , sum/kk, up, down);

        jdoubleArray ret = env->NewDoubleArray(3);
        jdouble ans[3];
        ans[0] = sum/kk;
        ans[1] = up;
        ans[2] = down;
        env->SetDoubleArrayRegion(ret, 0, 3, ans);
        std::string s = "CIS is called";
        return ret;
    }
}
