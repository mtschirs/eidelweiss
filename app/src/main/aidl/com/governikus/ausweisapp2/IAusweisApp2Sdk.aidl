// Interface reproduced from Governikus AusweisApp (EUPL-1.2).
package com.governikus.ausweisapp2;

import com.governikus.ausweisapp2.IAusweisApp2SdkCallback;
import android.nfc.Tag;

interface IAusweisApp2Sdk {
    boolean connectSdk(IAusweisApp2SdkCallback pCallback);
    boolean send(String pSessionId, String pMessageFromClient);
    boolean transmit(String pSessionId, in char[] pMessageFromClient);
    boolean updateNfcTag(String pSessionId, in Tag pTag);
}
