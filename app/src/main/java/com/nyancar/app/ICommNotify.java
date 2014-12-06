/*********************************************************************/
/* Copyright (c) 2014 TOYOTA MOTOR CORPORATION. All rights reserved. */
/*********************************************************************/

package com.nyancar.app;

public interface ICommNotify {

    public void notifyReceiveData(Object data);

    public void notifyBluetoothState(int nState);
}
