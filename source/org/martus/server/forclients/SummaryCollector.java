/*

The Martus(tm) free, social justice documentation and
monitoring software. Copyright (C) 2001-2004, Beneficent
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

package org.martus.server.forclients;

import java.util.Vector;

import org.martus.common.MartusConstants;
import org.martus.common.MartusUtilities;
import org.martus.common.database.Database;
import org.martus.common.database.DatabaseKey;
import org.martus.common.network.NetworkInterfaceConstants;
import org.martus.common.packet.BulletinHeaderPacket;
import org.martus.server.main.MartusServer;


public abstract class SummaryCollector implements Database.PacketVisitor
{
	protected SummaryCollector(MartusServer serverToUse, String authorAccountToUse, Vector retrieveTagsToUse)
	{
		server = serverToUse;
		authorAccountId = authorAccountToUse;
		retrieveTags = retrieveTagsToUse;
		
		summaries = new Vector();
	}
	
	public Database getDatabase()
	{
		return server.getDatabase();
	}
	
	public void visit(DatabaseKey key)
	{
		if(!BulletinHeaderPacket.isValidLocalId(key.getLocalId()))
			return;
		
		if(!MartusServer.keyBelongsToClient(key, authorAccountId))
			return;
		
		if(!isWanted(key))
			return;
		
		try
		{
			BulletinHeaderPacket bhp = server.loadBulletinHeaderPacket(getDatabase(), key);
			if(!isAuthorized(bhp))
				return;
			
			String summary = extractSummary(bhp, getDatabase(), retrieveTags);
			summaries.add(summary);
		}
		catch (Exception e)
		{
			server.log("Error in summary collector: " + getClass().getName());
			e.printStackTrace();
		}
	}
	
	abstract public String callerAccountId();
	abstract public boolean isWanted(DatabaseKey key);
	abstract public boolean isAuthorized(BulletinHeaderPacket bhp);
	
	public Vector collectSummaries()
	{
		server.getStore().visitAllBulletins(this);
		return summaries;	
	}
	
	public static String extractSummary(BulletinHeaderPacket bhp, Database db, Vector tags)
	{
		String summary = bhp.getLocalId() + MartusConstants.regexEqualsDelimeter;
		summary  += bhp.getFieldDataPacketId();
		if(tags.contains(NetworkInterfaceConstants.TAG_BULLETIN_SIZE))
		{
			int size = MartusUtilities.getBulletinSize(db, bhp);
			summary += MartusConstants.regexEqualsDelimeter + size;
		}
		if(tags.contains(NetworkInterfaceConstants.TAG_BULLETIN_DATE_SAVED))
		{
			summary += MartusConstants.regexEqualsDelimeter + bhp.getLastSavedTime();
		}
		if(tags.contains(NetworkInterfaceConstants.TAG_BULLETIN_VERSION_NUMBER))
		{
			int versionNumber = bhp.getHistory().size() + 1;
			summary += MartusConstants.regexEqualsDelimeter + versionNumber;
		}
		if(tags.contains(NetworkInterfaceConstants.TAG_BULLETIN_ORIGINAL_ANCESTOR))
		{
			String originalAncestorLocalId = (String)bhp.getHistory().get(0);
			summary += MartusConstants.regexEqualsDelimeter + originalAncestorLocalId;
		}
		return summary;
	}

	private MartusServer server;
	protected String authorAccountId;
	private Vector summaries;
	private Vector retrieveTags;
}