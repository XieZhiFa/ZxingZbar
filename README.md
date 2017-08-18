# ZxingZbar
极速扫码，Zxing Zbar 性能优化，极速秒扫
QQ 2227421573

**目前APP开发基本都用到二维码扫描的功能，可能这个大家一看就觉得。论坛Demo一大把，随便copy一个就可以使用了！起初我也是这么认为，网上确实有很多，随便找了一个改改确实也能用！！！用过一段时间来发现Bug太多，比如某些手机预览的时候有变形，图像有拉申，有些手机识别速度非常慢等问题，经过客户的几次吐槽后索性自己来做吧。**

网上有许多人用zxing 做相机预览，通过zbar进行解码， 速度是有提升， 但是也有几个问题：
 1. zxing 支持更多条码类型，例如Data Matrix 、 Axtec、 PDF417等类型的条码。
 2. zbar 并不支持以上举例的条码。
 3. zbar 对二维码有中文会乱码

    
**于是我就在想， 自己把这两个库结合起来，让用户自己选择用哪个解码库不就完了， 说干就干。下载了zxing运行了Demo 默认是横屏扫描的， 论坛有很多大神改竖屏的方案，本人挨个尝试后，发现改成竖屏后识别速度明显慢了许多!!!**

**其中最主要的原因是相机捕获的数据始终是横向的，即使你把预览框改成了竖向，但是数据依然是横向，因此需要将数据进行旋转，而zxing既然不支持横向扫条码（二维码就可以从四面去扫描），这个不得不吐槽zxing了，而旋转数据的代码在一台RedMi 3手机上测试发现既然用了600ms左右， 既然看了所有竖屏方案的文章都没有提及，知道慢的原因了就好办了看下面的步骤吧。**


## （一） 编译zxing##
我相信很多人下载了以后编译都会出现问题，zxing是用maven构建的项目， 那么编译需要用到maven。关于maven的下载安装配置环境变量等这里就不说了， 实在不会的就联系我吧，也可以直接用我编译好的包哈。

1. 本人于2017-08-02下载Zxing的demo 3.3.1版本，发现项目是用maven构造的，编译花了一下午，报了几个错最后是修改了根目录下的pom.xml文件， 最终编译成功， 生成了apk文件及两个jar包。
2. 编译就是直接解压进入zxing-master目录执行 mvn install 其中可能会报
Failed to execute goal org.apache.maven.plugins:maven-enforcer-plugin:1.4.1
Failed to execute goal org.apache.rat:apache-rat-plugin:0.12 这两个错误，我是简单粗暴的方式进行了修改pom.xml文件：
![此处输入图片的描述][1]


![此处输入图片的描述][2]


![此处输入图片的描述][3]


再执行mvn install 一段时间后就OK了。
![此处输入图片的描述][4]
  


  这时候在android-core 及core 目录下都会有 创建target目录并且会生成 android-core-3.3.1-SNAPSHOT.jar 及 core-3.3.1-SNAPSHOT.jar 两个jar包。
  

 3. 直接用android studio 打开项目选择zxing-master目录下的android即可，android studio会自动帮我们把相关的文件整合成as项目，此时会报错，因为没有jar包，只需要把上面两个jar包复制到zxing-master/android/app/libs即可。



## （二） 优化Zxing##
默认是横屏的， 这个实在是不能接受，不符合大家的习惯， 于是在zxing的lssuse中找到挺多大神给的方案，也在网上搜索半天，挨个试试以后，总结了一下，竖屏的方案主要修改地方为：
1. 修改activity CaptureActivity 中 android:screenOrientation="portrait" 固定为竖屏。


2. 修改CameraManager类 的getFramingRectInPreview方法，把     
rect.left = rect.left * cameraResolution.x / screenResolution.x;
rect.right = rect.right * cameraResolution.x / screenResolution.x;
rect.top = rect.top * cameraResolution.y / screenResolution.y;
rect.bottom = rect.bottom * cameraResolution.y / screenResolution.y;
改为：
rect.left = rect.left * cameraResolution.y / screenResolution.x;
rect.right = rect.right * cameraResolution.y / screenResolution.x;
rect.top = rect.top * cameraResolution.x / screenResolution.y;
rect.bottom = rect.bottom * cameraResolution.x / screenResolution.y;


3. 此时会发现扫描框并不是正方形，非常的高，于是再修改了 CameraManager类的getFramingRect()方法， 在 
int leftOffset = (screenResolution.x - width) / 2;
这行代码前面增加了
width = width + 50;
height = width;

4. 到目前为此，竖屏显示没问题了， 但是解码失败，扫半天都不行，原因上面讲了需要将相机数据进行旋转：
在DecodeHandler类的 decode方法中
PlanarYUVLuminanceSource source = activity.getCameraManager().buildLuminanceSource(data, width, height);
前增加
byte[] rotatedData = new byte[data.length];
for (int y = 0; y < height; y++) {
    for (int x = 0; x < width; x++)
        rotatedData[x * height + height - y - 1] = data[x + y * width];
}
int tmp = width;
width = height;
height = tmp;
data = rotatedData;


**到目前为止，扫码是没有问题了， 但是速度非常慢，可能在好一点的手机上没有感觉，但是本人在一台在红米3手机上测试,每次执行完decode方法都是在1300ms左右**

**解码速度的优化从decode方法着手**
## （三）编译Zbar ##
1. [Zbar主页][5] 点开android提示需要依赖libiconv这个库  http://www.gnu.org/software/libiconv于是百度半天后参考了别人的贴子，下载了 libiconv-1.15库，然后Zbar与libiconv一起编译。
2. 将Zbar-master目录下的 include zbar 及下载的 libiconv-1.15 一起复制到jni目录，这个jni目录可以随意在任何地方新建一文件夹，只要叫jni即可。
3. 将zbar-master/android/jni目录下的 三个文件Android.mk、Application.mk config.h 复制到 jni目录下。
4. 将Zbar-master/java/zbarjni.c也复制到jni目录下。
5. 由于修改了目录结构，所以需要修改Android.mk文件指定编译的c代码目录。
6. 如果有ndk环境，直接在jni目录下执行**ndk-build**大概10来分钟后会在jni目录下生成libs目录， 下面会有一堆so库。
7. 将Zbar-master/java目录下的java代码复制到项目中即可使用了。
8. 修改net.sourceforge.zbar下的 Image.java、ImageScanner.java、SymbolSet.java 在System.loadLibrary("zbarjni");前面把 libiconv库给加载进去 System.loadLibrary("iconv"); 注意这些名字要跟实际生成的so库名字一样哈。


**zbar的解码代码为：**

byte[] imageData = ...; //相机捕获的数据或图片数据
Image barcode = new Image(size.width, size.height, "Y800");
barcode.setData(imageData);
// 指定二维码在图片中的区域，也可以不指定，识别全图。
// barcode.setCrop(startX, startY, width, height);
String qrCodeString = null;
int result = mImageScanner.scanImage(barcode);
if (result != 0) {
    SymbolSet symSet = mImageScanner.getResults();
    for (Symbol sym : symSet)
        qrCodeString = sym.getData();
}
if (!TextUtils.isEmpty(qrCodeString)) {
    // 成功识别二维码，qrCodeString就是数据。
}



## （四）Zxing与Zbar的整合 ##
1. 知道zbar的解码过程以后就好办了， 直接到zxing的 DecodeHandler.java decode（）方法中增加代码:


Image barcode = new Image(width, height, "Y800");
barcode.setData(data);
Rect rect = activity.getCameraManager().getFramingRectInPreview();
if (rect != null) {
                /*
                    zbar 解码库,不需要将数据进行旋转,因此设置裁剪区域是的x为 top, y为left
                    设置了裁剪区域,解码速度快了近5倍左右
                 */
                barcode.setCrop(rect.top, rect.left, rect.width(), rect.height());    // 设置截取区域，也就是你的扫描框在图片上的区域.
}
ImageScanner mImageScanner = new ImageScanner();
int result = mImageScanner.scanImage(barcode);
if (result != 0) {
    SymbolSet symSet = mImageScanner.getResults();
        for (Symbol sym : symSet)
            resultQRcode = sym.getData();
}


2. 但是本人还是想保留zxing本身的解码能力，例如PDF417 zbar并不支持。

3. 持续优化decode，根据decode日志，发现数组的转换大概花了 600ms (测试机 RedMI 3)因此本人用c语言写了个数组转换的方法，编译成so库，发现调用c代码进行数组的转换，只需要30ms左右，快了接近20倍。具体代码在DecodeHandlerJni.c 文件中。
4. 在 PlanarYUVLuminanceSource source = activity.getCameraManager().buildLuminanceSource(data, width, height); 这行代码前面增加：
data = DecodeHandlerJni.dataHandler(data, data.length, width, height);
int tmp = width;
width = height;
height = tmp;
那么现在竖屏状态下的解码速度提升了一倍以上了。

为了兼容zxing与zbar 本人在设置界面增加了一个选择![此处输入图片的描述][6]

兼容模式就是用Zxing来解码， 而高速模式是用Zbar来解码。

**已知几个问题:** 
1. 连接扫描的时候，需要使用广播进行。
2. 扫码后播放声音默认是使用MediaPlayer，建议使用SoundPool， 因为连续扫码过快的时候，MediaPlayer播放不及时。
3. 扫码界面未增加闪光灯及连接扫描按钮。
4. 扫码框没有美化，例如增加四个角等等。

有问题欢迎交流：QQ 2227421573

  [1]: https://raw.githubusercontent.com/XieZhiFa/ZxingZbar/master/images/pom_1.jpg
  [2]: https://raw.githubusercontent.com/XieZhiFa/ZxingZbar/master/images/pom_2.png
  [3]: https://raw.githubusercontent.com/XieZhiFa/ZxingZbar/master/images/pom_3.png
  [4]: https://raw.githubusercontent.com/XieZhiFa/ZxingZbar/master/images/maven_build.png
  [5]: https://github.com/ZBar/ZBar
  [6]: https://raw.githubusercontent.com/XieZhiFa/ZxingZbar/master/images/scan_model.png
