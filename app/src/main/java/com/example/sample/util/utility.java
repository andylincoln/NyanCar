package com.example.sample.util;

import java.nio.ByteBuffer;

/**
 * Created by alincoln on 12/6/14.
 */
public class Utility {

    public static int TIMESTAMP = 1;
    public static int OPERATION_ACCELERATOR_POSITION = 2;
    public static int OPERATION_BRAKE_PEDAL_STATUS = 3;
    public static int OPERATION_PARKING_BRAKE_STATUS = 4;
    public static int OPERATION_AT_SHIFT_POSITION = 5;
    public static int OPERATION_MANUAL_MODE_STATUS = 6;
    public static int OPERATION_TRANSMISSION_GEAR_POSITION = 7;
    public static int OPERATION_STEERING_WHEEL_ANGLE = 8;
    public static int OPERATION_DOORS_STATUS = 9;
    public static int OPERATION_SEATBELTS_STATUS = 10;
    public static int OPERATION_HEADLAMP_STATUS = 11;
    public static int STATUS_ENGINE_REVOLUTION_SPEED = 12;
    public static int STATUS_VEHICLE_SPEED = 13;
    public static int STATUS_ACCELERATION_FRONT_BACK = 14;
    public static int STATUS_ACCELERATION_TRANSVERSE = 15;
    public static int STATUS_YAW_RATE = 16;
    public static int STATUS_ODOMETER = 17;
    public static int STATUS_FUEL_CONSUMPTION = 18;
    public static int STATUS_OUTSIDE_TEMP = 19;
    public static int STATUS_ENGINE_COOLANT_TEMP = 20;
    public static int STATUS_ENGINE_OIL_TEMP = 21;
    public static int STATUS_TRANSMISSION_TYPE = 22;
    public static int GPS_UTC = 23;
    public static int GPS_LATITUDE = 24;
    public static int GPS_NORTH_SOUTH_LATITUDE = 25;
    public static int GPS_LONGITUDE = 26;
    public static int GPS_EAST_WEST_LONGITUDE = 27;
    public static int GPS_QUALITY_INDICATOR = 28;
    public static int GPS_NUM_SATELLITES = 29;
    public static int GPS_ANTENNA_ALTITUDE = 30;
    public static int GPS_ALTITUDE = 31;
    public static int GPS_GEOIDAL_SEPARATION = 32;
    public static int GPS_GEOIDAL_SEPARATION_UNITS = 33;
    public static int GPS_SPEED_OVER_GROUND = 34;
    public static int GPS_COURSE_OVERGROUND = 35;
    public static int GPS_UTC_DAY = 36;
    public static int GPS_UTC_MONTH = 37;
    public static int GPS_UTC_YEAR = 38;
    public static int GPS_2D_3D = 39;
    public static int GPS_PDOP = 40;
    public static int GPS_HDOP = 41;
    public static int GPS_VDOP = 42;

    /* Create the message of vehicle signal request */
    public static ByteBuffer createRequest(int signal){

		/* e.g.) request of Engine Revolution Speed */
        byte[] buf = {0x7e,0x00,0x00,0x01,0x01,0x00,0x00,0x00,0x00,0x7f};
        int length = buf.length;

		/* Set the message length */
        buf[1] = (byte)(((length - 6) >> 8) & 0xff);
        buf[2] = (byte)((length - 6) & 0xff);

		/* Set the request signal ID */
        buf[6] = (byte)(signal);

		/* Calculate and set the CRC */
        int crc = calcCRC(buf, 1, buf.length - 4);

		/* Convert endian from little to big */
        buf[length - 3] = (byte)((crc >> 8) & 0xff);
        buf[length - 2] = (byte)(crc & 0xff);

        return ByteBuffer.wrap(buf);

    }

    public static boolean isCarInfoGetFrame(ByteBuffer frame){

        byte tmp = frame.get(3);

        if (tmp == 0x11){
            return true;
        }

        return false;
    }

    public static int toUint16Value(byte[] buffer, int index) {

        int value = 0;

        value |= (buffer[index + 0] << 8) & 0x0000ff00;
        value |= (buffer[index + 1] << 0) & 0x000000ff;

        return value & 0xffff;
    }

    public static long toUint32Value(byte[] buffer, int index) {

        int value = 0;

        value |= (buffer[index + 0] << 24) & 0xff000000;
        value |= (buffer[index + 1] << 16) & 0x00ff0000;
        value |= (buffer[index + 2] <<  8) & 0x0000ff00;
        value |= (buffer[index + 3] <<  0) & 0x000000ff;

        return value & 0xffffffffL;
    }

    public static int calcCRC(byte[] buffer, int index, int length) {

        int crcValue = 0x0000;
        boolean flag;
        boolean c15;

        for( int i = 0; i < length; i++ ) {

            for(int j = 0; j < 8; j++) {

                flag = ( (buffer[i + index] >> (7 - j) ) & 0x0001)==1;
                c15  = ((crcValue >> 15 & 1) == 1);
                crcValue <<= 1;

                if(c15 ^ flag) {
                    crcValue ^= 0x1021;
                }
            }
        }

        crcValue ^= 0x0000;
        crcValue &= 0x0000ffff;

        return crcValue;
    }

}
