/*
 * Copyright 2020 Blockchain Technology Partners.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.blockchaintp.besu.daml;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateKey;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateValue;
import com.blockchaintp.besu.daml.protobuf.DamlLogEvent;
import com.daml.daml_lf_dev.DamlLf.Archive;
import com.google.protobuf.ByteString;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.ethereum.core.DefaultEvmAccount;
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import net.bytebuddy.utility.RandomString;

public class MutableAccountLedgerStateTest {

  private static final int RANDOM_STRING_LENGTH = 24;

  private Map.Entry<MutableAccount, Map<UInt256, UInt256>> getMockState() {
    Map<UInt256, UInt256> stateMap = new HashMap<>();
    MutableAccount account = mock(MutableAccount.class);
    when(account.getStorageValue(any(UInt256.class))).thenAnswer(new Answer<UInt256>() {
      @Override
      public UInt256 answer(InvocationOnMock invocation) throws Throwable {
        UInt256 address = invocation.getArgument(0);
        stateMap.getOrDefault(address, UInt256.ZERO);
        return stateMap.getOrDefault(address, UInt256.ZERO);
      }
    });

    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        UInt256 address = invocation.getArgument(0);
        UInt256 value = invocation.getArgument(1);
        stateMap.put(address, value);
        return null;
      }
    }).when(account).setStorageValue(any(UInt256.class), any(UInt256.class));

    return Map.entry(account, stateMap);
  }

  @Test
  public void testRoundTripDamlState() {
    Entry<MutableAccount, Map<UInt256, UInt256>> mockAccount = getMockState();
    MessageFrame frame = mock(MessageFrame.class);
    WorldUpdater updater = mock(WorldUpdater.class);
    when(frame.getWorldState()).thenReturn(updater);

    MutableAccount account = mockAccount.getKey();
    DefaultEvmAccount evmAccount = new DefaultEvmAccount(account);
    when(updater.getOrCreate(any())).thenReturn(evmAccount);
    LedgerState<DamlLogEvent> state = new MutableAccountLedgerState(frame);

    String packageId = RandomString.make(RANDOM_STRING_LENGTH);
    String content = RandomString.make(RANDOM_STRING_LENGTH * 10);
    Archive archive = Archive.newBuilder().setPayload(ByteString.copyFromUtf8(content)).build();
    DamlStateKey dsKey = DamlStateKey.newBuilder().setPackageId(packageId).build();
    DamlStateValue dsValue = DamlStateValue.newBuilder().setArchive(archive).build();
    state.setDamlState(dsKey.toByteString(), dsValue.toByteString());

    ByteString retVal = state.getDamlState(dsKey.toByteString());
    System.out.println(retVal.toStringUtf8());
    System.out.println(dsValue.toByteString().toStringUtf8());
    assertTrue("retVal.length != dsValue.length", retVal.toStringUtf8().length() == dsValue.toByteString().toStringUtf8().length());
    assertTrue(dsValue.toByteString().equals(retVal));

    byte[] zeroBytes = new byte[128];
    ByteString zeroBs = ByteString.copyFrom(zeroBytes);
    ByteString zeroKey = ByteString.copyFromUtf8("ZERO KEY");
    state.setDamlState(zeroKey, zeroBs);

    retVal = state.getDamlState(zeroKey);
    assertTrue(retVal.toByteArray().length == 128);

  }
}
