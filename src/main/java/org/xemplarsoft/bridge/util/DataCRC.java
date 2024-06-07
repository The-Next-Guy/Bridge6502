package org.xemplarsoft.bridge.util;

public final class DataCRC {
    private DataCRC(){}

    private static final int POLYNOMIAL = 0x1021;
    private static final int INITIAL_VALUE = 0xFFFF;

    public static int calculateCRC(byte[] bytes, int offset, int len) {
        int crc = INITIAL_VALUE;
        for (int j = offset; j < len; j++) {
            byte b = bytes[j];
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x8000) != 0) {
                    crc = (crc << 1) ^ POLYNOMIAL;
                } else {
                    crc <<= 1;
                }
            }
        }
        return crc & 0xFFFF;
    }

    public static byte[] toBytes(int[] crc){
        byte[] ret = new byte[crc.length * 2];
        for(int i = 0; i < crc.length; i++){
            ret[(i << 1)]     = (byte)(crc[i] & 0xFF);
            ret[(i << 1) + 1] = (byte)((crc[i] >> 8) & 0xFF);
        }

        return ret;
    }

    public static byte[] toBytes(int crc){
        byte[] ret = new byte[2];
        ret[0]     = (byte)(crc & 0xFF);
        ret[1] = (byte)((crc >> 8) & 0xFF);

        return ret;
    }
}
