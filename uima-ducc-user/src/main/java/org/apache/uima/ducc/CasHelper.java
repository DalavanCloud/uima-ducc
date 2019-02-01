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
package org.apache.uima.ducc;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.analysis_engine.annotator.AnnotatorInitializationException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.Type;

/**
 * 
 * Return CAS's DocumentText, or if it exists, the Workitem:inputspec
 * 
 */
public class CasHelper {

	public static String getId(CAS cas) {
	  Type mWorkitemType;
	  Feature mInputspecFeature;
		String retVal = null;
		if (cas != null) {
			retVal = cas.getDocumentText();
			try {
			  // Get a reference to the "Workitem" Type
			  mWorkitemType = cas.getTypeSystem().getType("org.apache.uima.ducc.Workitem");
			  if (mWorkitemType == null) {
			    throw new AnalysisEngineProcessException(AnnotatorInitializationException.TYPE_NOT_FOUND,
			            new Object[] { CasHelper.class.getName(), "org.apache.uima.ducc.Workitem" });
			  }
			  // Get a reference to the "sendToALL" Feature
			  mInputspecFeature = mWorkitemType.getFeatureByBaseName("inputspec");
			  if (mInputspecFeature == null) {
			    throw new AnalysisEngineProcessException(AnnotatorInitializationException.FEATURE_NOT_FOUND,
			            new Object[] { CasHelper.class.getName(), "org.apache.uima.ducc.Workitem:inputspec" });
			  }
			  FSIterator<FeatureStructure> it = cas.getIndexRepository().getAllIndexedFS(mWorkitemType);
			  if (it.isValid()) {
			    FeatureStructure wi = it.get();
			    if (wi != null) {
			      String id = wi.getStringValue(mInputspecFeature);
						if(id != null) {
						  retVal = id;
						}
			    }
				}
			} 
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		return retVal;
	}
}
