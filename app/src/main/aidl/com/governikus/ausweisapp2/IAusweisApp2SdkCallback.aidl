// Interface reproduced from Governikus AusweisApp (EUPL-1.2).
package com.governikus.ausweisapp2;

interface IAusweisApp2SdkCallback {
    void sessionIdGenerated(String pSessionId);
    void receive(String pJson);
    void sdkDisconnected();
}
