/*
 * Copyright (c) 2017, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.andes.server.handler;

import org.wso2.andes.AMQException;
import org.wso2.andes.dtx.XidImpl;
import org.wso2.andes.framing.DtxSetTimeoutOkBody;
import org.wso2.andes.framing.amqp_0_91.DtxSetTimeoutBodyImpl;
import org.wso2.andes.framing.amqp_0_91.MethodRegistry_0_91;
import org.wso2.andes.kernel.AndesException;
import org.wso2.andes.kernel.dtx.UnknownDtxBranchException;
import org.wso2.andes.protocol.AMQConstant;
import org.wso2.andes.server.AMQChannel;
import org.wso2.andes.server.protocol.AMQProtocolSession;
import org.wso2.andes.server.state.AMQStateManager;
import org.wso2.andes.server.state.StateAwareMethodListener;
import org.wso2.andes.server.txn.DtxNotSelectedException;
import org.wso2.andes.transport.DtxXaStatus;

import javax.transaction.xa.Xid;

public class DtxSetTimeoutHandler implements StateAwareMethodListener<DtxSetTimeoutBodyImpl> {
    private static DtxSetTimeoutHandler _instance = new DtxSetTimeoutHandler();

    public static DtxSetTimeoutHandler getInstance() {
        return _instance;
    }

    private DtxSetTimeoutHandler() {
    }

    @Override
    public void methodReceived(AMQStateManager stateManager, DtxSetTimeoutBodyImpl body, int channelId)
            throws AMQException {
        Xid xid = new XidImpl(body.getBranchId(), body.getFormat(), body.getGlobalId());
        AMQProtocolSession session = stateManager.getProtocolSession();

        AMQChannel channel = session.getChannel(channelId);

        if (channel == null) {
            throw body.getChannelNotFoundException(channelId);
        }

        try {
            channel.setDtxTransactionTimeout(xid, body.getTimeout());

            MethodRegistry_0_91 methodRegistry = (MethodRegistry_0_91) session.getMethodRegistry();
            DtxSetTimeoutOkBody dtxSetTimeoutOkBody = methodRegistry.createDtxSetTimeoutOkBody(DtxXaStatus.XA_OK
                                                                                                       .getValue());
            session.writeFrame(dtxSetTimeoutOkBody.generateFrame(channelId));
        } catch (UnknownDtxBranchException e) {
            throw body.getChannelException(AMQConstant.NOT_ALLOWED,
                                           "Error ending dtx. Unknown branch for the given " + xid , e);
        } catch (DtxNotSelectedException e) {
            throw body.getChannelException(AMQConstant.NOT_ALLOWED, "Not a distributed transacted session", e);
        } catch (AndesException e) {
            throw body.getChannelException(AMQConstant.INTERNAL_ERROR, "Internal error occurred while setting " +
                    "dtx.timeout", e);
        }
    }
}
