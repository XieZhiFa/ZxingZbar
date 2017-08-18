package com.google.zxing.client.android;

/**
 * Created by Android on 2017/8/17.
 */

public class DecodeHandlerJni {

    static{
        System.loadLibrary("decodeHandler");
    }

    public static native byte[] dataHandler(byte[] by, int length, int width, int height);
}
