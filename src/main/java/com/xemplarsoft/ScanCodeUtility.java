package com.xemplarsoft;

public class ScanCodeUtility {
    public static int[] data = new int[]{
            0,0,
            1,168,
            3,164,
            4,162,
            5,160,
            6,161,
            7,171,
            9,169,
            10,167,
            11,165,
            12,163,
            13,11,
            14,96,
            17,193,
            18,194,
            20,196,
            21,81,
            22,49,
            26,90,
            27,83,
            28,65,
            29,87,
            30,50,
            33,67,
            34,88,
            35,68,
            36,69,
            37,52,
            38,51,
            41,32,
            42,86,
            43,70,
            44,84,
            45,82,
            46,53,
            49,78,
            50,66,
            51,72,
            52,71,
            53,89,
            54,54,
            58,77,
            59,74,
            60,85,
            61,55,
            62,56,
            65,44,
            66,75,
            67,73,
            68,79,
            69,48,
            70,57,
            73,46,
            74,47,
            75,76,
            76,59,
            77,80,
            78,45,
            82,39,
            84,91,
            85,48,
            88,200,
            89,194,
            90,0x0A,
            91,93,
            93,92,
            102,0x08,
            105,49,
            107,52,
            108,0x37,
            112,0x30,
            113,46,
            114,50,
            115,53,
            116,54,
            117,56,
            118,33,
            120,170,
            121,0x2B,
            122,0x33,
            123,0x2D,
            124,0x2A,
            125,57
    };
    public static void main(String[] args) throws Exception{
        int index = 0;
        for(int i = 0; i < (data.length / 2); i++){
            while(index < data[i << 1]){
                if(index % 8 == 0){
                    System.out.print("    .byte ");
                }
                System.out.print("$00");
                index++;
                if(index % 8 == 0){
                    System.out.println();
                } else {
                    System.out.print(", ");
                }
            }
            if(index % 8 == 0){
                System.out.print("    .byte ");
            }
            System.out.print("$");
            if(data[(i << 1) + 1] < 16) System.out.print("0");
            System.out.print(Integer.toHexString(data[(i << 1) + 1]));
            index++;
            if(index % 8 == 0){
                System.out.println();
            } else {
                System.out.print(", ");
            }

        }
        while(index < 128){
            if(index % 8 == 0){
                System.out.print("    .byte ");
            }
            System.out.print("$00");
            index++;
            if(index % 8 == 0){
                System.out.println();
            } else {
                System.out.print(", ");
            }
        }
    }
}
