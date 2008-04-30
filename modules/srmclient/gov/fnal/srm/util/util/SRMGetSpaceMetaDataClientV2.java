//______________________________________________________________________________
//
// $Id: SRMGetSpaceMetaDataClientV2.java,v 1.2 2006-11-14 20:41:46 timur Exp $
// $Author: timur $
//
// 
// created 11/06 by Timur Perelmutov (timur@fnal.gov)
//
//______________________________________________________________________________

/*
COPYRIGHT STATUS:
  Dec 1st 2001, Fermi National Accelerator Laboratory (FNAL) documents and
  software are sponsored by the U.S. Department of Energy under Contract No.
  DE-AC02-76CH03000. Therefore, the U.S. Government retains a  world-wide
  non-exclusive, royalty-free license to publish or reproduce these documents
  and software for U.S. Government purposes.  All documents and software
  available from this server are protected under the U.S. and Foreign
  Copyright Laws, and FNAL reserves all rights.
 
 
 Distribution of the software available from this server is free of
 charge subject to the user following the terms of the Fermitools
 Software Legal Information.
 
 Redistribution and/or modification of the software shall be accompanied
 by the Fermitools Software Legal Information  (including the copyright
 notice).
 
 The user is asked to feed back problems, benefits, and/or suggestions
 about the software to the Fermilab Software Providers.
 
 
 Neither the name of Fermilab, the  URA, nor the names of the contributors
 may be used to endorse or promote products derived from this software
 without specific prior written permission.
 
 
 
  DISCLAIMER OF LIABILITY (BSD):
 
  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
  "AS IS" AND ANY EXPRESS OR IMPLIED  WARRANTIES, INCLUDING, BUT NOT
  LIMITED TO, THE IMPLIED  WARRANTIES OF MERCHANTABILITY AND FITNESS
  FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL FERMILAB,
  OR THE URA, OR THE U.S. DEPARTMENT of ENERGY, OR CONTRIBUTORS BE LIABLE
  FOR  ANY  DIRECT, INDIRECT,  INCIDENTAL, SPECIAL, EXEMPLARY, OR
  CONSEQUENTIAL DAMAGES  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
  OF SUBSTITUTE  GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
  BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY  OF
  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT  OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE  POSSIBILITY OF SUCH DAMAGE.
 
 
  Liabilities of the Government:
 
  This software is provided by URA, independent from its Prime Contract
  with the U.S. Department of Energy. URA is acting independently from
  the Government and in its own private capacity and is not acting on
  behalf of the U.S. Government, nor as its contractor nor its agent.
  Correspondingly, it is understood and agreed that the U.S. Government
  has no connection to this software and in no manner whatsoever shall
  be liable for nor assume any responsibility or obligation for any claim,
  cost, or damages arising out of or resulting from the use of the software
  available from this server.
 
 
  Export Control:
 
  All documents and software available from this server are subject to U.S.
  export control laws.  Anyone downloading information from this server is
  obligated to secure any necessary Government licenses before exporting
  documents or software obtained from this server.
 */

/*
 * SrmReserveSpaceClientV2.java
 *
 * Created on January 28, 2003, 2:54 PM
 */

package gov.fnal.srm.util;
import java.util.HashMap;
import java.util.Iterator;
import org.globus.util.GlobusURL;
import org.dcache.srm.v2_2.ISRM;
import org.dcache.srm.client.SRMClientV2;
import org.ietf.jgss.GSSCredential;
import java.io.IOException;
import java.text.DateFormat;
import org.apache.axis.types.URI;
import org.dcache.srm.v2_2.*;
import org.dcache.srm.util.RequestStatusTool;

public class SRMGetSpaceMetaDataClientV2 extends SRMClient  {
	private GlobusURL srmURL;
	private org.ietf.jgss.GSSCredential credential = null;
	private ISRM srmv2;

	public SRMGetSpaceMetaDataClientV2(Configuration configuration, 
				       GlobusURL url) { 
		super(configuration);
		srmURL=url;
		try {
			credential = getGssCredential();
		}
		catch (Exception e) {
			credential = null;
			System.err.println("Couldn't getGssCredential.");
		}
	}
	
	public void connect() throws Exception {
		
		srmv2 = new SRMClientV2(srmURL, 
					getGssCredential(),
					configuration.getRetry_timeout(),
					configuration.getRetry_num(),
					configuration.getLogger(),
					doDelegation, 
					fullDelegation,
					gss_expected_name,
					configuration.getWebservice_path());
	}
	
	public void start() throws Exception {
		try {
			if (credential.getRemainingLifetime() < 60) 
				throw new Exception(
					"Remaining lifetime of credential is less than a minute.");
		}
		catch (org.ietf.jgss.GSSException gsse) {
			throw gsse;
		}
		try { 
                        String[] tokens = configuration.getSpaceTokensList();
                	SrmGetSpaceMetaDataRequest request = new SrmGetSpaceMetaDataRequest(); 
                        request.setArrayOfSpaceTokens(new ArrayOfString(tokens));
			
			SrmGetSpaceMetaDataResponse response = srmv2.srmGetSpaceMetaData(request);


			if ( response == null ) { 
				throw new IOException(" null SrmGetSpaceMetaDataResponse");
			}
			
			TReturnStatus rs     = response.getReturnStatus();
			if ( rs == null) { 
				throw new IOException(" null TReturnStatus ");	
			}
			if (RequestStatusTool.isFailedRequestStatus(rs)) {
				throw new IOException("SrmGetSpaceMetaData failed, unexpected or failed return status : "+
						      rs.getStatusCode()+" explanation="+rs.getExplanation());
			}
                        TMetaDataSpace [] spaceMetaDatas  = response.getArrayOfSpaceDetails().getSpaceDataArray();
                        for(int i = 0; i< spaceMetaDatas.length; ++i) {
                            TMetaDataSpace spaceMetaData = spaceMetaDatas[i];
                            
                            System.out.println("Space Reservation with token="+spaceMetaData.getSpaceToken());
                            if(spaceMetaData.getStatus().getStatusCode() != TStatusCode.SRM_SUCCESS) {
                                System.out.println("\t StatusCode="+spaceMetaData.getStatus().getStatusCode()+
                                        " explanation="+spaceMetaData.getStatus().getExplanation());
                                continue;
                                        
                            }
                            System.out.println("\t           owner:"+spaceMetaData.getOwner());
                            System.out.println("\t       totalSize:"+spaceMetaData.getTotalSize());
                            System.out.println("\t  guaranteedSize:"+spaceMetaData.getGuaranteedSize());
                            System.out.println("\t      unusedSize:"+spaceMetaData.getUnusedSize());
                            System.out.println("\tlifetimeAssigned:"+spaceMetaData.getLifetimeAssigned());
                            System.out.println("\t    lifetimeLeft:"+spaceMetaData.getLifetimeLeft());
                            TRetentionPolicyInfo policyInfo = spaceMetaData.getRetentionPolicyInfo();
                            if(policyInfo != null) {
                                System.out.println("\t   accessLatency:"+policyInfo.getAccessLatency());
                                System.out.println("\t retentionPolicy:"+policyInfo.getRetentionPolicy());
                            }
                            
                        }

 		}
		catch(Exception e) {
			throw e;
		}
	}
}
