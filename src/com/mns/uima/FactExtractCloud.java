package com.mns.uima;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.uima.UIMAFramework;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.ArrayFS;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.impl.XmiCasDeserializer;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.util.CasCreationUtils;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;
import org.apache.uima.util.XMLInputSource;

import com.cloudant.client.api.ClientBuilder;
import com.cloudant.client.api.CloudantClient;
import com.cloudant.client.api.Database;
import com.google.gson.JsonObject;
import com.mns.uima.utils.CASUtils;
import com.mns.uima.utils.DocumentDetails;

/**
 * Template for building Annotators
 * <p>
 * @author      martin.saunders@uk.ibm.com
 * @version     1.0 $Revision: 184 $            
 */
public class FactExtractCloud extends JCasAnnotator_ImplBase {
	private static final String SENTENCETYPE = "uima.tt.SentenceAnnotation";
	private static final String PARAGRAPHTYPE = "uima.tt.ParagraphAnnotation";
	private static final String LEMMATYPE = "uima.tt.Lemma";
	private static final String LEMMAKEY = "key";


	// AE parameters
	private static final String PARAM_ACCOUNT = "account";
	private static final String PARAM_USERNAME = "username";
	private static final String PARAM_PASSWORD = "password";
	private static final String PARAM_DATABASE = "database";
	private static final String PARAM_TRIGGER = "trigger";


	// Global Variables
	private Logger logger = null;
	private JCas jcas = null;
	private Database db = null;
	private List<Type> persistTypes = new ArrayList<Type>();
	//private String text = null;

	// Global parameters
	private static String pAccount;   
	private static String pUsername;   
	private static String pPassword;   
	private static String pDatabase;   
	private static String pTriggerFeature;   

	/**
	 * Read configuration and initialise annotator with configuration.
	 * <p>
	 *
	 * @param  aContext 
	 * @throws ResourceInitializationException
	 */
	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
		logger = aContext.getLogger();	
		logger.log(Level.INFO, "FactExtractCloud: initializing:");
		pAccount = CASUtils.getConfigurationStringValue(aContext, null, PARAM_ACCOUNT);
		pUsername = CASUtils.getConfigurationStringValue(aContext, null, PARAM_USERNAME);
		pPassword = CASUtils.getConfigurationStringValue(aContext, null, PARAM_PASSWORD);
		pDatabase = CASUtils.getConfigurationStringValue(aContext, null, PARAM_DATABASE);
		pTriggerFeature = CASUtils.getConfigurationStringValue(aContext, null, PARAM_TRIGGER);
		CloudantClient client = ClientBuilder.account(pAccount)
				.username(pUsername)
				.password(pPassword)
				.build();
		db = client.database(pDatabase, true);
		//logger.log(Level.INFO,"Server Version: " + client.serverVersion());	
	}

	/** 
	 * @see org.apache.uima.analysis_component.JCasAnnotator_ImplBase#process(org.apache.uima.jcas.JCas)
	 */

	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		logger.log(Level.INFO, "FactExtractCloud: processing:");

		this.jcas=aJCas;
		//DocumentDetails.extractDocumentDetails(jcas);

		//this.text=jcas.getDocumentText();
				
		persistTypes = initPersistTypeList(jcas);
		for (Type persistType: persistTypes) {		
			try {
				AnnotationIndex index =  jcas.getAnnotationIndex(persistType);
				@SuppressWarnings("rawtypes")
				FSIterator iterator = index.iterator();
				List<Feature> props = persistType.getFeatures();
				List<Feature> featureNames = new ArrayList<Feature>();
				for (Feature ft : props) {
					if (ft.getDomain().getName().equals(persistType.getName()) && !ft.getShortName().equals("ruleId") && !ft.getShortName().equals(pTriggerFeature)) 
						featureNames.add(ft);				
				}
				String typeNameStr = persistType.getName();
				for (iterator.moveToFirst(); iterator.isValid(); iterator.moveToNext()) {
					AnnotationFS afs = (AnnotationFS)iterator.get();
					logger.log(Level.INFO,afs.getCoveredText());
					JsonObject json = new JsonObject();
					//json.addProperty("_id", "test-doc-id-2");
					json.addProperty("type", typeNameStr);
					json.addProperty("coveredText", afs.getCoveredText());
					json.addProperty("begin", afs.getBegin());
					json.addProperty("end", afs.getEnd());
					for (Feature name : featureNames){
						if (name.getRange().isPrimitive()) {
							json.addProperty(name.getShortName(), afs.getFeatureValueAsString(name));
						} else if (name.getRange().isArray()) { // try the covered text on the first element 
							FeatureStructure fs = ((ArrayFS) afs.getFeatureValue(name)).get(0);
							if (fs != null) {
								if (fs.getType().getFeatureByBaseName("begin") != null) // it's an annotation
									json.addProperty(name.getShortName(), ((AnnotationFS) fs).getCoveredText());
							}
						} else {
							FeatureStructure fs = afs.getFeatureValue(name);
							String fName = fs.getType().getName();
							if (fName.equals(SENTENCETYPE) || fName.equals(PARAGRAPHTYPE))
								json.addProperty(name.getShortName(), ((AnnotationFS) fs).getCoveredText());
						}
					}
					int y = 3;
					db.save(json);
				}
			} catch (Exception e) {		
			}
		}
		//logger.log(Level.INFO, "FactExtractCloud: processed document: " + DocumentDetails.title);
		logger.log(Level.INFO, "FactExtractCloud: processed document: ");
	}

	/**
	 * Iterates through all user types in the typesystem 
	 * to identify those that have been marked for persistence.
	 * <p>
	 * Retunrs a list of those types.
	 *
	 * @param  jc JCas
	 */
	private List<Type> initPersistTypeList(JCas jc) {
		List<Type> pTypes = new ArrayList<Type>();
		TypeSystem typeSystem = jc.getTypeSystem();
		Iterator typeIterator = typeSystem.getTypeIterator();
		Type t;
		while (typeIterator.hasNext()) {
			t = (Type) typeIterator.next();
			if (!t.getName().startsWith("uima.")) {
				List<Feature> fts = t.getFeatures();
				if (t.getFeatureByBaseName(pTriggerFeature)!=null) {
					if (!t.getName().contains(".en."))
						pTypes.add(t);	
				}
			}
		}
		return pTypes;
	}

	/**
	 * Test annotators process method outside of a UIMA pipeline. 
	 * Primary usage is debugging with eclipse.
	 * <p>
	 * Necessary xml and xmi files may be generated in ICA Studio using
	 * the "Save as XMI" feature after annotating a document.
	 *
	 * @param  typeFile xml typesytem file
	 * @param  casFile xmi of exported cas
	 */
	public void TestAnnotator(String typeFile, String casFile) {

		try {
			logger = UIMAFramework.getLogger(FactExtractCloud.class);

			XMLInputSource xmlIn = new XMLInputSource(typeFile);
			TypeSystemDescription tsDesc = UIMAFramework.getXMLParser().parseTypeSystemDescription(xmlIn);
			CAS cas = CasCreationUtils.createCas( tsDesc, null, null ); 
			XmiCasDeserializer.deserialize(new FileInputStream(casFile), cas, false);
			// and process
			jcas = cas.getJCas();
			CloudantClient client = ClientBuilder.account(pAccount)
					.username(pUsername)
					.password(pPassword)
					.build();
			db = client.database(pDatabase, true);

			process(jcas);
		}
		catch(Exception e) {
			logger.log(Level.SEVERE, "Error initialising the JCas: " + e.toString(),e);
		}
	}

	/**
	 * Setter for AE parameters used in debugging.
	 * <p>
	 * @param pExample
	 */
	public static void setpAccount(String pAccount) {
		FactExtractCloud.pAccount = pAccount;
	}

	public static void setpUsername(String pUsername) {
		FactExtractCloud.pUsername = pUsername;
	}

	public static void setpPassword(String pPassword) {
		FactExtractCloud.pPassword = pPassword;
	}

	public static void setpDatabase(String pDatabase) {
		FactExtractCloud.pDatabase = pDatabase;
	}

	public static void setpTriggerFeature(String pTriggerFeature) {
		FactExtractCloud.pTriggerFeature = pTriggerFeature;
	}

}
