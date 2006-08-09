/*

The Martus(tm) free, social justice documentation and
monitoring software. Copyright (C) 2002-2006, Beneficent
Technology, Inc. (Benetech).

Martus is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either
version 2 of the License, or (at your option) any later
version with the additions and exceptions described in the
accompanying Martus license file entitled "license.txt".

It is distributed WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, including warranties of fitness of purpose or
merchantability.  See the accompanying Martus License and
GPL license for more details on the required license terms
for this software.

You should have received a copy of the GNU General Public
License along with this program; if not, write to the Free
Software Foundation, Inc., 59 Temple Place - Suite 330,
Boston, MA 02111-1307, USA.

*/

package org.martus.server.formirroring;

import java.io.StringWriter;
import java.util.Vector;

import org.martus.common.LoggerToNull;
import org.martus.common.MartusUtilities;
import org.martus.common.bulletin.BulletinConstants;
import org.martus.common.crypto.MartusCrypto;
import org.martus.common.crypto.MockMartusSecurity;
import org.martus.common.database.DatabaseKey;
import org.martus.common.database.MockDatabase;
import org.martus.common.database.MockServerDatabase;
import org.martus.common.network.NetworkInterfaceConstants;
import org.martus.common.network.mirroring.CallerSideMirroringGateway;
import org.martus.common.network.mirroring.MirroringInterface;
import org.martus.common.packet.BulletinHeaderPacket;
import org.martus.common.packet.UniversalId;
import org.martus.server.forclients.MockMartusServer;
import org.martus.util.Base64;
import org.martus.util.TestCaseEnhanced;

public class TestSupplierSideMirroringHandler extends TestCaseEnhanced
{
	public TestSupplierSideMirroringHandler(String name) 
	{
		super(name);
	}

	protected void setUp() throws Exception
	{
		super.setUp();
		supplier = new FakeServerSupplier();
		supplierSecurity = supplier.getSecurity();
		handler = new SupplierSideMirroringHandler(supplier, supplierSecurity);
		
		callerSecurity = MockMartusSecurity.createClient();
		callerAccountId = callerSecurity.getPublicKeyString();
		
		authorSecurity = MockMartusSecurity.createOtherClient();
	}

	public void testBadSignature() throws Exception
	{
		Vector parameters = new Vector();
		String sig = callerSecurity.createSignatureOfVectorOfStrings(parameters);
		parameters.add("Hello");
		Vector result = handler.request(callerAccountId, parameters, sig);
		assertEquals(1, result.size());
		assertEquals(NetworkInterfaceConstants.SIG_ERROR, result.get(0));
	}

	public void testNonStringCommand() throws Exception
	{
		supplier.authorizedCaller = callerAccountId;

		Vector parameters = new Vector();
		parameters.add(new Integer(3));
		String sig = callerSecurity.createSignatureOfVectorOfStrings(parameters);
		Vector result = handler.request(callerAccountId, parameters, sig);
		assertEquals(1, result.size());
		assertEquals(NetworkInterfaceConstants.INVALID_DATA, result.get(0));
	}
	
	public void testUnknownCommand() throws Exception
	{
		supplier.authorizedCaller = callerAccountId;

		String accountId = callerSecurity.getPublicKeyString();
		Vector parameters = new Vector();
		parameters.add("This will never be a valid command!");
		String sig = callerSecurity.createSignatureOfVectorOfStrings(parameters);
		Vector result = handler.request(accountId, parameters, sig);
		assertEquals(1, result.size());
		assertEquals(NetworkInterfaceConstants.UNKNOWN_COMMAND, result.get(0));
	}
	
	
	public void testPing() throws Exception
	{
		supplier.authorizedCaller = callerAccountId;

		String accountId = callerSecurity.getPublicKeyString();
		Vector parameters = new Vector();
		parameters.add(MirroringInterface.CMD_MIRRORING_PING);
		String sig = callerSecurity.createSignatureOfVectorOfStrings(parameters);
		Vector result = handler.request(accountId, parameters, sig);
		assertEquals(2, result.size());
		assertEquals(NetworkInterfaceConstants.OK, result.get(0));
		Vector publicInfo = (Vector)result.get(1);
		String publicKey = (String)publicInfo.get(0);
		String gotSig = (String)publicInfo.get(1);
		MartusUtilities.validatePublicInfo(publicKey, gotSig, callerSecurity);
		assertEquals(supplierSecurity.getPublicKeyString(), publicInfo.get(0));
	}
	
	public void testUnsignedPing() throws Exception
	{
		String accountId = callerSecurity.getPublicKeyString();
		Vector parameters = new Vector();
		parameters.add(MirroringInterface.CMD_MIRRORING_PING);
		String sig = "";
		Vector result = handler.request(accountId, parameters, sig);
		assertEquals(2, result.size());
		assertEquals(NetworkInterfaceConstants.OK, result.get(0));
		Vector publicInfo = (Vector)result.get(1);
		String publicKey = (String)publicInfo.get(0);
		String gotSig = (String)publicInfo.get(1);
		MartusUtilities.validatePublicInfo(publicKey, gotSig, callerSecurity);
		assertEquals(supplierSecurity.getPublicKeyString(), publicInfo.get(0));
	}

	public void testAnonymousPing() throws Exception
	{
		String accountId = "";
		Vector parameters = new Vector();
		parameters.add(MirroringInterface.CMD_MIRRORING_PING);
		String sig = "";
		Vector result = handler.request(accountId, parameters, sig);
		assertEquals(2, result.size());
		assertEquals(NetworkInterfaceConstants.OK, result.get(0));
		Vector publicInfo = (Vector)result.get(1);
		String publicKey = (String)publicInfo.get(0);
		String gotSig = (String)publicInfo.get(1);
		MartusUtilities.validatePublicInfo(publicKey, gotSig, callerSecurity);
		assertEquals(supplierSecurity.getPublicKeyString(), publicInfo.get(0));
	}

	public void testGetAllAccountsNotAuthorized() throws Exception
	{
		Vector parameters = new Vector();
		parameters.add(MirroringInterface.CMD_MIRRORING_LIST_ACCOUNTS);
		String sig = callerSecurity.createSignatureOfVectorOfStrings(parameters);
		Vector result = handler.request(callerAccountId, parameters, sig);
		assertEquals(1, result.size());
		assertEquals(NetworkInterfaceConstants.NOT_AUTHORIZED, result.get(0));
	}

	public void testGetAllAccountsNoneAvailable() throws Exception
	{
		supplier.authorizedCaller = callerAccountId;
		
		Vector parameters = new Vector();
		parameters.add(MirroringInterface.CMD_MIRRORING_LIST_ACCOUNTS);
		String sig = callerSecurity.createSignatureOfVectorOfStrings(parameters);
		Vector result = handler.request(callerAccountId, parameters, sig);
		assertEquals(2, result.size());
		assertEquals(NetworkInterfaceConstants.OK, result.get(0));
		Vector accounts = (Vector)result.get(1);
		assertEquals(0, accounts.size());
	}

	public void testGetAllAccounts() throws Exception
	{
		supplier.authorizedCaller = callerAccountId;

		String accountId1 = "first sample account";
		supplier.addAccountToMirror(accountId1);
		String accountId2 = "second sample account";
		supplier.addAccountToMirror(accountId2);

		Vector parameters = new Vector();
		parameters.add(MirroringInterface.CMD_MIRRORING_LIST_ACCOUNTS);
		String sig = callerSecurity.createSignatureOfVectorOfStrings(parameters);
		Vector result = handler.request(callerAccountId, parameters, sig);
		assertEquals(NetworkInterfaceConstants.OK, result.get(0));
		Vector accounts = (Vector)result.get(1);
		assertEquals(2, accounts.size());
		assertContains(accountId1, accounts);
		assertContains(accountId2, accounts);
	}
	
	public void testListBulletinsNotAuthorized() throws Exception
	{
		Vector parameters = new Vector();
		parameters.add(MirroringInterface.CMD_MIRRORING_LIST_SEALED_BULLETINS);
		parameters.add("account id to ignore");
		String sig = callerSecurity.createSignatureOfVectorOfStrings(parameters);
		Vector result = handler.request(callerAccountId, parameters, sig);
		assertEquals(1, result.size());
		assertEquals(NetworkInterfaceConstants.NOT_AUTHORIZED, result.get(0));
	}
	
	public void testListBulletinsBadAuthorAccountId() throws Exception
	{
		supplier.authorizedCaller = callerAccountId;

		Vector parameters = new Vector();
		parameters.add(MirroringInterface.CMD_MIRRORING_LIST_SEALED_BULLETINS);
		parameters.add(new Integer(3));
		String sig = callerSecurity.createSignatureOfVectorOfStrings(parameters);
		Vector result = handler.request(callerAccountId, parameters, sig);
		assertEquals(1, result.size());
		assertEquals(NetworkInterfaceConstants.INVALID_DATA, result.get(0));
	}
	
	public void testListSealedBulletins() throws Exception
	{
		String authorAccountId = authorSecurity.getPublicKeyString();
		
		BulletinHeaderPacket bhp1 = new BulletinHeaderPacket(authorSecurity);
		bhp1.setStatus(BulletinConstants.STATUSSEALED);
		Vector result1 = writeSampleHeaderPacket(bhp1);
		
		BulletinHeaderPacket bhp2 = new BulletinHeaderPacket(authorSecurity);
		bhp2.setStatus(BulletinConstants.STATUSSEALED);
		Vector result2 = writeSampleHeaderPacket(bhp2);

		BulletinHeaderPacket bhpDraft = new BulletinHeaderPacket(authorSecurity);
		bhpDraft.setStatus(BulletinConstants.STATUSDRAFT);
		Vector result3 = writeSampleHeaderPacket(bhpDraft);
		
		supplier.authorizedCaller = callerAccountId;

		Vector parameters = new Vector();
		parameters.add(MirroringInterface.CMD_MIRRORING_LIST_SEALED_BULLETINS);
		parameters.add(authorAccountId);
		String sig = callerSecurity.createSignatureOfVectorOfStrings(parameters);
		Vector result = handler.request(callerAccountId, parameters, sig);
		assertEquals(NetworkInterfaceConstants.OK, result.get(0));
		Vector infos = (Vector)result.get(1);
		assertEquals(2, infos.size());
		assertContains(result1, infos);
		assertContains(result2, infos);
		assertNull(result3);
	}
	
	public void testListAvailableIds() throws Exception
	{
		String authorAccountId = authorSecurity.getPublicKeyString();
		
		BulletinHeaderPacket bhp1 = new BulletinHeaderPacket(authorSecurity);
		bhp1.setStatus(BulletinConstants.STATUSSEALED);
		Vector result1 = writeSampleAvailableIDPacket(bhp1);
		
		BulletinHeaderPacket bhp2 = new BulletinHeaderPacket(authorSecurity);
		bhp2.setStatus(BulletinConstants.STATUSSEALED);
		Vector result2 = writeSampleAvailableIDPacket(bhp2);

		BulletinHeaderPacket bhpDraft = new BulletinHeaderPacket(authorSecurity);
		bhpDraft.setStatus(BulletinConstants.STATUSDRAFT);
		Vector result3 = writeSampleAvailableIDPacket(bhpDraft);
		
		supplier.authorizedCaller = callerAccountId;

		Vector parameters = new Vector();
		parameters.add(MirroringInterface.CMD_MIRRORING_LIST_AVAILABLE_IDS);
		parameters.add(authorAccountId);
		String sig = callerSecurity.createSignatureOfVectorOfStrings(parameters);
		Vector result = handler.request(callerAccountId, parameters, sig);
		assertEquals(NetworkInterfaceConstants.OK, result.get(0));
		Vector infos = (Vector)result.get(1);
		assertEquals(3, infos.size());
		assertContains("result1 missing?", result1, infos);
		assertContains("result2 missing?",result2, infos);
		assertContains("result3 missing?",result3, infos);
		
		CallerSideMirroringGateway gateway = new CallerSideMirroringGateway(handler);
		LoggerToNull logger = new LoggerToNull();
		MockMartusServer server = new MockMartusServer();

		MirroringRetriever mirroringRetriever = new MirroringRetriever(server.getStore(), gateway, "Dummy IP", logger);
		Vector returnedListWeWantToMirror = mirroringRetriever.listOnlyPacketsThatWeWantUsingBulletinMirroringInformation(authorSecurity.getPublicKeyString(), infos);
		int bulletinsVerified = 0;
		for (int i = 0; i < returnedListWeWantToMirror.size(); i++)
		{
			BulletinMirroringInformation returnedInfo = (BulletinMirroringInformation) returnedListWeWantToMirror.get(i);
			bulletinsVerified = verifyStatus(bulletinsVerified, returnedInfo, bhp1, "BHP1");
			bulletinsVerified = verifyStatus(bulletinsVerified, returnedInfo, bhp2, "BHP2");
			bulletinsVerified = verifyStatus(bulletinsVerified, returnedInfo, bhpDraft, "BHPDRAFT");
		}		
		assertEquals(3, bulletinsVerified);
		server.deleteAllFiles();
	}

	private int verifyStatus(int bulletinsVerified, BulletinMirroringInformation returnedInfo, BulletinHeaderPacket bhp, String tag)
	{
		if(returnedInfo.getUid().equals(bhp.getUniversalId()))
		{
			assertEquals("Status for "+tag+" not correct?",bhp.getStatus(), returnedInfo.getStatus());
			++bulletinsVerified;
		}
		return bulletinsVerified;
	}

	public void testGetBulletinUploadRecordNotFound() throws Exception
	{
		supplier.authorizedCaller = callerAccountId;

		Vector parameters = new Vector();
		parameters.add(MirroringInterface.CMD_MIRRORING_GET_BULLETIN_UPLOAD_RECORD);
		parameters.add("No such account");
		parameters.add("No such bulletin");
		String sig = callerSecurity.createSignatureOfVectorOfStrings(parameters);
		Vector result = handler.request(callerAccountId, parameters, sig);
		assertEquals(NetworkInterfaceConstants.NOT_FOUND, result.get(0));
		assertEquals(1, result.size());
	}

	public void testGetBulletinUploadRecordSealedOld() throws Exception
	{
		supplier.authorizedCaller = callerAccountId;

		UniversalId uid = UniversalId.createDummyUniversalId();
		String bur = "This pretends to be a BUR";
		supplier.addBur(uid, bur, BulletinConstants.STATUSSEALED);
		Vector parameters = new Vector();
		parameters.add(MirroringInterface.CMD_MIRRORING_GET_BULLETIN_UPLOAD_RECORD);
		parameters.add(uid.getAccountId());
		parameters.add(uid.getLocalId());
		String sig = callerSecurity.createSignatureOfVectorOfStrings(parameters);
		Vector result = handler.request(callerAccountId, parameters, sig);
		assertEquals(NetworkInterfaceConstants.OK, result.get(0));
		assertEquals(2, result.size());
		assertEquals(bur, ((Vector)result.get(1)).get(0));
	}

	public void testGetBulletinUploadRecordSealedNew() throws Exception
	{
		supplier.authorizedCaller = callerAccountId;

		UniversalId uid = UniversalId.createDummyUniversalId();
		String bur = "This pretends to be a BUR";
		supplier.addBur(uid, bur, BulletinConstants.STATUSSEALED);
		Vector parameters = new Vector();
		parameters.add(MirroringInterface.CMD_MIRRORING_GET_BULLETIN_UPLOAD_RECORD);
		parameters.add(uid.getAccountId());
		parameters.add(uid.getLocalId());
		parameters.add(BulletinConstants.STATUSSEALED);
		String sig = callerSecurity.createSignatureOfVectorOfStrings(parameters);
		Vector result = handler.request(callerAccountId, parameters, sig);
		assertEquals(NetworkInterfaceConstants.OK, result.get(0));
		assertEquals(2, result.size());
		assertEquals(bur, ((Vector)result.get(1)).get(0));
	}

	public void testGetBulletinUploadRecordDraft() throws Exception
	{
		supplier.authorizedCaller = callerAccountId;

		UniversalId uid = UniversalId.createDummyUniversalId();
		String bur = "This pretends to be a BUR";
		supplier.addBur(uid, bur, BulletinConstants.STATUSDRAFT);
		Vector parameters = new Vector();
		parameters.add(MirroringInterface.CMD_MIRRORING_GET_BULLETIN_UPLOAD_RECORD);
		parameters.add(uid.getAccountId());
		parameters.add(uid.getLocalId());
		parameters.add(BulletinConstants.STATUSDRAFT);
		String sig = callerSecurity.createSignatureOfVectorOfStrings(parameters);
		Vector result = handler.request(callerAccountId, parameters, sig);
		assertEquals(NetworkInterfaceConstants.OK, result.get(0));
		assertEquals(2, result.size());
		assertEquals(bur, ((Vector)result.get(1)).get(0));
	}

	public void testGetBulletinChunkNotAuthorized() throws Exception
	{
		Vector parameters = new Vector();
		parameters.add(MirroringInterface.CMD_MIRRORING_GET_BULLETIN_CHUNK);
		parameters.add("account id to ignore");
		String sig = callerSecurity.createSignatureOfVectorOfStrings(parameters);
		Vector result = handler.request(callerAccountId, parameters, sig);
		assertEquals(1, result.size());
		assertEquals(NetworkInterfaceConstants.NOT_AUTHORIZED, result.get(0));
	}
	
	public void testGetBulletinChunkBadAuthorAccountId() throws Exception
	{
		supplier.authorizedCaller = callerAccountId;

		Vector parameters = new Vector();
		parameters.add(MirroringInterface.CMD_MIRRORING_GET_BULLETIN_CHUNK);
		parameters.add(new Integer(3));
		String sig = callerSecurity.createSignatureOfVectorOfStrings(parameters);
		Vector result = handler.request(callerAccountId, parameters, sig);
		assertEquals(1, result.size());
		assertEquals(NetworkInterfaceConstants.INVALID_DATA, result.get(0));
	}
	
	public void testGetBulletinChunkBadParameter() throws Exception
	{
		supplier.authorizedCaller = callerAccountId;

		Vector parameters = new Vector();
		parameters.add(MirroringInterface.CMD_MIRRORING_GET_BULLETIN_CHUNK);
		parameters.add("pretend account");
		parameters.add("pretend localid");
		parameters.add(new Integer(3));
		parameters.add("bad maxChunkSize");
		String sig = callerSecurity.createSignatureOfVectorOfStrings(parameters);
		Vector result = handler.request(callerAccountId, parameters, sig);
		assertEquals(1, result.size());
		assertEquals(NetworkInterfaceConstants.INVALID_DATA, result.get(0));
	}
	
	public void testGetBulletinChunk() throws Exception
	{
		final String authorAccountId = "a";
		final String bulletinLocalId = "b";
		final int offset = 123;
		final int maxChunkSize = 456;
		
		supplier.returnResultTag = NetworkInterfaceConstants.CHUNK_OK;
		supplier.authorizedCaller = callerAccountId;
		String returnZipData = Base64.encode("zip data");
		UniversalId uid = UniversalId.createFromAccountAndLocalId(authorAccountId, bulletinLocalId);
		supplier.addZipData(uid, returnZipData);

		Vector parameters = new Vector();
		parameters.add(MirroringInterface.CMD_MIRRORING_GET_BULLETIN_CHUNK);
		parameters.add(authorAccountId);
		parameters.add(bulletinLocalId);
		parameters.add(new Integer(offset));
		parameters.add(new Integer(maxChunkSize));
		String sig = callerSecurity.createSignatureOfVectorOfStrings(parameters);
		Vector result = handler.request(callerAccountId, parameters, sig);

		assertEquals(authorAccountId, supplier.gotAccount);
		assertEquals(bulletinLocalId, supplier.gotLocalId);
		assertEquals(offset, supplier.gotChunkOffset);
		assertEquals(maxChunkSize, supplier.gotMaxChunkSize);
	
		assertEquals(2, result.size());
		assertEquals(NetworkInterfaceConstants.CHUNK_OK, result.get(0));
		Vector details = (Vector)result.get(1);
		assertEquals(new Integer(supplier.getChunkSize(uid) * 3), details.get(0));
		assertEquals(new Integer(supplier.getChunkSize(uid)), details.get(1));
		assertEquals(returnZipData, details.get(2));
	}

	public void testGetBulletinChunkTypo() throws Exception
	{
		final String authorAccountId = "a";
		final String bulletinLocalId = "b";
		final int offset = 123;
		final int maxChunkSize = 456;
		
		supplier.returnResultTag = NetworkInterfaceConstants.CHUNK_OK;
		supplier.authorizedCaller = callerAccountId;
		String returnZipData = Base64.encode("zip data");
		UniversalId uid = UniversalId.createFromAccountAndLocalId(authorAccountId, bulletinLocalId);
		supplier.addZipData(uid, returnZipData);


		Vector parameters = new Vector();
		parameters.add(MirroringInterface.CMD_MIRRORING_GET_BULLETIN_CHUNK_TYPO);
		parameters.add(authorAccountId);
		parameters.add(bulletinLocalId);
		parameters.add(new Integer(offset));
		parameters.add(new Integer(maxChunkSize));
		String sig = callerSecurity.createSignatureOfVectorOfStrings(parameters);
		Vector result = handler.request(callerAccountId, parameters, sig);

		assertEquals(authorAccountId, supplier.gotAccount);
		assertEquals(bulletinLocalId, supplier.gotLocalId);
		assertEquals(offset, supplier.gotChunkOffset);
		assertEquals(maxChunkSize, supplier.gotMaxChunkSize);
	
		assertEquals(2, result.size());
		assertEquals(NetworkInterfaceConstants.CHUNK_OK, result.get(0));
		Vector details = (Vector)result.get(1);
		assertEquals(new Integer(supplier.getChunkSize(uid) * 3), details.get(0));
		assertEquals(new Integer(supplier.getChunkSize(uid)), details.get(1));
		assertEquals(returnZipData, details.get(2));
	}

	Vector writeSampleHeaderPacket(BulletinHeaderPacket bhp) throws Exception
	{
		StringWriter writer = new StringWriter();
		byte[] sigBytes = bhp.writeXml(writer, authorSecurity);
		DatabaseKey key = DatabaseKey.createSealedKey(bhp.getUniversalId());
		if(bhp.getStatus().equals(BulletinConstants.STATUSDRAFT))
			return null;
		String sigString = Base64.encode(sigBytes);
		supplier.addBulletinToMirror(key, sigString);
		
		Vector info = new Vector();
		info.add(bhp.getLocalId());
		info.add(sigString);
		return info;
	}
	
	Vector writeSampleAvailableIDPacket(BulletinHeaderPacket bhp) throws Exception
	{

		StringWriter writer = new StringWriter();
		byte[] sigBytes = bhp.writeXml(writer, authorSecurity);
		DatabaseKey key = null;
		if(bhp.getStatus().equals(BulletinConstants.STATUSDRAFT))
			key = DatabaseKey.createDraftKey(bhp.getUniversalId());
		else
			key = DatabaseKey.createSealedKey(bhp.getUniversalId());

		String sigString = Base64.encode(sigBytes);
		MockDatabase db = new MockServerDatabase();
		db.writeRecord(key, "Some text");
		supplier.addAvailableIdsToMirror(db, key, sigString);
		
		BulletinMirroringInformation bulletinInfo = new BulletinMirroringInformation(db, key, sigString);
		Vector info = bulletinInfo.getInfoWithLocalId();
		return info;
	}

	FakeServerSupplier supplier;
	MartusCrypto supplierSecurity;
	SupplierSideMirroringHandler handler;
	MartusCrypto callerSecurity;
	String callerAccountId;
	
	MartusCrypto authorSecurity;
}

