/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
package org.apache.uima.ducc.cli;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.uima.ducc.api.DuccMessage;
import org.apache.uima.ducc.api.IDuccMessageProcessor;
import org.apache.uima.ducc.common.crypto.Crypto;
import org.apache.uima.ducc.common.exception.DuccRuntimeException;
import org.apache.uima.ducc.common.utils.DuccPropertiesResolver;
import org.apache.uima.ducc.common.utils.Utils;
import org.apache.uima.ducc.transport.dispatcher.DuccEventHttpDispatcher;
import org.apache.uima.ducc.transport.event.CancelServiceDuccEvent;
import org.apache.uima.ducc.transport.event.CancelServiceReplyDuccEvent;
import org.apache.uima.ducc.transport.event.DuccEvent;
import org.apache.uima.ducc.transport.event.cli.JobReplyProperties;
import org.apache.uima.ducc.transport.event.cli.JobRequestProperties;
import org.apache.uima.ducc.transport.event.cli.ServiceSpecificationProperties;


/**
 * Cancel a DUCC service
 */

public class DuccServiceCancel extends DuccUi {
	
	private IDuccMessageProcessor duccMessageProcessor = new DuccMessage();
	
	public DuccServiceCancel() {
	}
	
	public DuccServiceCancel(IDuccMessageProcessor duccMessageProcessor) {
		this.duccMessageProcessor = duccMessageProcessor;
	}
	
	@SuppressWarnings("static-access")
	private void addOptions(Options options) {
		options.addOption(OptionBuilder
				.withDescription(DuccUiConstants.desc_help).hasArg(false)
				.withLongOpt(DuccUiConstants.name_help).create());
		options.addOption(OptionBuilder
				.withDescription(DuccUiConstants.desc_role_administrator).hasArg(false)
				.withLongOpt(DuccUiConstants.name_role_administrator).create());
		options.addOption(OptionBuilder
				.withArgName(DuccUiConstants.parm_service_id)
				.withDescription(makeDesc(DuccUiConstants.desc_service_id,DuccUiConstants.exmp_service_id)).hasArg()
				.withLongOpt(DuccUiConstants.name_service_id).create());
	}
	
	protected int help(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.setWidth(DuccUiConstants.help_width);
		formatter.printHelp(DuccServiceCancel.class.getName(), options);
		return 1;
	}
	
	public int run(String[] args) throws Exception {
		JobRequestProperties serviceRequestProperties = new JobRequestProperties();
		/*
		 * parser is not thread safe?
		 */
		synchronized(DuccUi.class) {	
			Options options = new Options();
			addOptions(options);
			CommandLineParser parser = new PosixParser();
			CommandLine commandLine = parser.parse(options, args);
			/*
			 * give help & exit when requested
			 */
			if (commandLine.hasOption(DuccUiConstants.name_help)) {
				return help(options);
			}
			if(commandLine.getOptions().length == 0) {
				return help(options);
			}
			/*
			 * require DUCC_HOME 
			 */
			String ducc_home = Utils.findDuccHome();
			if(ducc_home == null) {
				duccMessageProcessor.err("missing required environment variable: DUCC_HOME");
				return 1;
			}
			/*
			 * detect duplicate options
			 */
			if (DuccUiUtilities.duplicate_options(duccMessageProcessor, commandLine)) {
				return 1;
			}
			/*
			 * marshal user
			 */
			String user = DuccUiUtilities.getUser();
			serviceRequestProperties.setProperty(ServiceSpecificationProperties.key_user, user);
			String property = DuccPropertiesResolver.getInstance().getProperty(DuccPropertiesResolver.ducc_signature_required);
			if(property != null) {
				String signatureRequiredProperty = property.trim().toLowerCase();
				if(signatureRequiredProperty.equals("on")) {
					Crypto crypto = new Crypto(System.getProperty("user.home"));
					byte[] cypheredMessage = crypto.encrypt(user);
					serviceRequestProperties.put(ServiceSpecificationProperties.key_signature, cypheredMessage);
				}
			}
			/*
			 * marshal command line options into properties
			 */
			Option[] optionList = commandLine.getOptions();
			for (int i=0; i<optionList.length; i++) {
				Option option = optionList[i];
				String name = option.getLongOpt();
				String value = option.getValue();
				if(value == null) {
					value = "";
				}
				serviceRequestProperties.setProperty(name, value);
			}
		}
		// trim
		DuccUiUtilities.trimProperties(serviceRequestProperties);
		
        String port = 
                DuccPropertiesResolver.
                  getInstance().
                    getProperty(DuccPropertiesResolver.ducc_orchestrator_http_port);
        if ( port == null ) {
          throw new DuccRuntimeException("Unable to Submit a Service. Ducc Orchestrator HTTP Port Not Defined. Add ducc.orchestrator.http.port ducc.properties");
        }
        String orNode = 
                DuccPropertiesResolver.
                  getInstance().
                    getProperty(DuccPropertiesResolver.ducc_orchestrator_node);
        if ( orNode == null ) {
          throw new DuccRuntimeException("Unable to Submit a Service. Ducc Orchestrator Node Not Defined. Add ducc.orchestrator.node to ducc.properties");
        }
        
        String targetUrl = "http://"+orNode+":"+port+"/or";
        DuccEventHttpDispatcher duccEventDispatcher = new DuccEventHttpDispatcher(targetUrl);        
        CancelServiceDuccEvent CancelServiceDuccEvent = new CancelServiceDuccEvent();
        CancelServiceDuccEvent.setProperties(serviceRequestProperties);
        DuccEvent duccRequestEvent = CancelServiceDuccEvent;
        DuccEvent duccReplyEvent = null;
        CancelServiceReplyDuccEvent cancelServiceReplyDuccEvent = null;
        try {
        	duccReplyEvent = duccEventDispatcher.dispatchAndWaitForDuccReply(duccRequestEvent);
        }
        finally {
          duccEventDispatcher.close();
        	//context.stop();
        }
        /*
         * process reply
         */
        cancelServiceReplyDuccEvent = (CancelServiceReplyDuccEvent) duccReplyEvent;
        // TODO handle null & rejected possibilities here
    	String jobId = cancelServiceReplyDuccEvent.getProperties().getProperty(JobReplyProperties.key_id);
    	String msg = cancelServiceReplyDuccEvent.getProperties().getProperty(JobReplyProperties.key_message);
    	duccMessageProcessor.out("Service"+" "+jobId+" "+msg);
		return 0;
	}
	
	public static void main(String[] args) {
		try {
			DuccServiceCancel duccServiceCancel = new DuccServiceCancel();
			int rc = duccServiceCancel.run(args);
            System.exit(rc == 0 ? 0 : 1);
		} catch (Exception e) {
			e.printStackTrace();
            System.exit(1);
		}
	}
	
}
