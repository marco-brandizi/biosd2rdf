package uk.ac.ebi.fg.biosd.biosd2rdf.java2rdf.mapping;

import static uk.ac.ebi.fg.biosd.biosd2rdf.utils.BioSdOntologyTermResolver.BIOPORTAL_ONTOLOGIES;
import static uk.ac.ebi.fg.java2rdf.utils.Java2RdfUtils.hashUriSignature;
import static uk.ac.ebi.fg.java2rdf.utils.Java2RdfUtils.urlEncode;
import static uk.ac.ebi.fg.java2rdf.utils.NamespaceUtils.uri;
import static uk.ac.ebi.fg.java2rdf.utils.OwlApiUtils.assertAnnotationData;
import static uk.ac.ebi.fg.java2rdf.utils.OwlApiUtils.assertData;
import static uk.ac.ebi.fg.java2rdf.utils.OwlApiUtils.assertIndividual;
import static uk.ac.ebi.fg.java2rdf.utils.OwlApiUtils.assertLink;

import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.cxf.xjc.runtime.DataTypeAdapter;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.vocab.XSDVocabulary;

import uk.ac.ebi.bioportal.webservice.client.BioportalClient;
import uk.ac.ebi.bioportal.webservice.model.OntologyClass;
import uk.ac.ebi.bioportal.webservice.model.OntologyClassMapping;
import uk.ac.ebi.fg.biosd.biosd2rdf.utils.BioSdOntologyTermResolver;
import uk.ac.ebi.fg.core_model.expgraph.properties.BioCharacteristicValue;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.expgraph.properties.Unit;
import uk.ac.ebi.fg.core_model.toplevel.Accessible;
import uk.ac.ebi.fg.java2rdf.mapping.RdfMapperFactory;
import uk.ac.ebi.fg.java2rdf.mapping.RdfMappingException;
import uk.ac.ebi.fg.java2rdf.mapping.properties.PropertyRdfMapper;
import uk.ac.ebi.fg.java2rdf.utils.Java2RdfUtils;
import uk.ac.ebi.onto_discovery.api.OntologyTermDiscoverer.DiscoveredTerm;

/**
 * Maps a sample property like 'Characteristics[organism]' to proper RDF/OWL statements. OBI and other relevant ontologies
 * are used for that. <a href = 'http://www.ebi.ac.uk/rdf/documentation/biosamples'>Here</a> you can find examples of 
 * what this class produces. 
 *
 * <dl><dt>date</dt><dd>Apr 29, 2013</dd></dl>
 * @author Marco Brandizi
 *
 */
@SuppressWarnings ( "rawtypes" )
public class ExpPropValueRdfMapper<T extends Accessible> extends PropertyRdfMapper<T, ExperimentalPropertyValue>
{
	/**
	 * If true, the Bioportal ontology mappings service is used to fetch mappings between ontology terms that are discovered
	 * by ZOOMA.
	 */
	public static final String FETCH_ONTOLOGY_MAPPINGS_PROP_NAME = "uk.ac.ebi.fg.biosd.biosd2rdf.fetchOntologyMappings";
	
	/**
	 * If this is true, the old linked data model is supported, in addition to the new one.
	 * See <a href = 'https://www.ebi.ac.uk/rdf/biosd/newschema16'>here</a> for details.
	 */
	public static final boolean OLD_MODEL_SUPPORT_FLAG = false; 
	
	private static BioSdOntologyTermResolver otermResolver = new BioSdOntologyTermResolver ();

	public final boolean fetchOntologyMappings = "true".equals ( 
		System.getProperty ( FETCH_ONTOLOGY_MAPPINGS_PROP_NAME, "false" )
	);
	
	/**
	 * A facility to work out composed numerical/date/unit-equipped values and decompose them into RDF describing
	 * their components. See below how this is used.
	 *  
	 */
	private class PropValueComponents
	{
		public Double value = null, lo = null, hi = null;
		public Date date = null, dateTime = null;
		public String valueLabel = null;
		public String unitLabel = null;
		public String unitClassUri = null;
		
		public PropValueComponents ( ExperimentalPropertyValue<?> pval )
		{
			Unit u = pval.getUnit ();
			if ( u != null ) 
			{
				this.unitLabel =  StringUtils.trimToNull ( u.getTermText () );
			
				// See if the unit object has an explicit ontology term attached
				this.unitClassUri = otermResolver.getOntologyTermURI ( u.getOntologyTerms (), this.unitLabel );
				
				if ( this.unitClassUri == null )
					// No? Then, try with the unit ontology
					this.unitClassUri = otermResolver.getUnitUri ( this.unitLabel );
			}

			String pvalStr = StringUtils.trimToNull ( pval.getTermText () );
			if ( pvalStr == null ) return;
			
			this.valueLabel = pvalStr;
			if ( this.unitLabel != null ) this.valueLabel += " " + this.unitLabel;
			
			// Start checking a middle separator, to see if it is a range
			String chunks[] = pvalStr.substring ( 0, Math.min ( pvalStr.length (), 300 ) ).split ( "(\\-|\\.\\.|\\, )" );
			
			if ( chunks != null && chunks.length == 2 )
			{
				chunks [ 0 ] = StringUtils.trimToNull ( chunks [ 0 ] );
				chunks [ 1 ] = StringUtils.trimToNull ( chunks [ 1 ] );
				
				// Valid chunks?
				if ( chunks [ 0 ] != null && chunks [ 1 ] != null )
				{
					// Number chunks?
					if ( NumberUtils.isNumber ( chunks [ 0 ] ) && NumberUtils.isNumber ( chunks [ 1 ] ) )
					{
						try {
							this.lo = Double.parseDouble ( chunks [ 0 ] );
							this.hi = Double.parseDouble ( chunks [ 1 ] );
							return;
						} 
						catch ( NumberFormatException nex ) {
							this.lo = this.hi = null;
							// Just ignore all in case of problems
						}
					}
				} // if valid chunks
			} // if there are chunks
			
			// Is it a single number?
			if ( NumberUtils.isNumber ( pvalStr ) ) 
				try {
					this.value = Double.parseDouble ( pvalStr );
					return;
				}
				catch ( NumberFormatException nex ) {
					this.value = null;
					// Just ignore all in case of problems
			}
				
			// Or maybe a single date?
			// TODO: factorise these constants
			try {
				this.date = DateUtils.parseDate ( pvalStr, "dd'/'MM'/'yyyy", "dd'-'MM'-'yyyy", "yyyyMMdd" );
			}
			catch ( ParseException dex ) {
				// Just ignore all in case of problems
				this.date = null;
			}
			// Or even date+time?
			try {
				this.dateTime = DateUtils.parseDate ( pvalStr, 
					"dd'/'MM'/'yyyy HH:mm:ss", "dd'/'MM'/'yyyy HH:mm", 
					"dd'-'MM'-'yyyy HH:mm:ss", "dd'-'MM'-'yyyy HH:mm",
					"yyyyMMdd'-'HHmmss", "yyyyMMdd'-'HHmm"  
				);
			}
			catch ( ParseException dex ) {
				// Just ignore all in case of problems
				this.dateTime = null;
			}
		}
		
		/**
		 * Asserts data values from the structure discovered from an experimental property value string, using the BIOSD/RDF
		 * URI for such property value. 
		 */
		public void map ( String pvalUri )
		{
			OWLOntology onto = ExpPropValueRdfMapper.this.getMapperFactory ().getKnowledgeBase ();
			
			assertData ( onto, pvalUri, uri ( "dc-terms", "title" ), this.valueLabel );
			assertData ( onto, pvalUri, uri ( "rdfs", "label" ), this.valueLabel );
			assertAnnotationData ( onto, pvalUri, uri ( "atlas", "propertyValue" ), this.valueLabel );
			
			// Say something about the unit, if you've one
			//
			if ( this.unitClassUri != null )
			{
				// As usually, we have a (recyclable) unit individual, which is an instance of a unit class.
				// I'd like to use unitClassUri + prefix for this, but it's recommended not to use other's namespaces
				// TODO: experiment OWL2 punning?
				//
				String unitInstUri = uri ( "biosd", "unit#" + Java2RdfUtils.hashUriSignature ( this.unitClassUri ) );
				assertLink ( onto, pvalUri, uri ( "sio", "SIO_000221" ), unitInstUri ); // 'has unit'
				assertIndividual ( onto, unitInstUri, this.unitClassUri );
				assertAnnotationData ( onto, unitInstUri, uri ( "rdfs", "label" ), this.unitLabel ); // let's report user details somewhere
				assertAnnotationData ( onto, unitInstUri, uri ( "dc-terms", "title" ), this.unitLabel ); 
				
				// This is 'unit of measurement'. this is implied by the range of 'has unit', but let's report it, for sake of 
				// completeness and to ease things when no inference is computed
				assertIndividual ( onto, unitInstUri, uri ( "sio", "SIO_000074" ) );
			}
			
			
			// Is it a single number? 
			//
			if ( this.value != null ) {
				// has value
				assertData ( onto, pvalUri, uri ( "sio", "SIO_000300") , String.valueOf ( this.value ), XSDVocabulary.DOUBLE.toString () );
				return;
			}
				
			// Or a single date? 
			// 
			if ( this.date != null ) {
				// has value
				assertData ( onto, pvalUri, uri ( "sio", "SIO_000300") , DataTypeAdapter.printDate ( this.date ), XSDVocabulary.DATE.toString () );
				return;
			}
			
			// Or date+time?
			if ( this.dateTime != null ) {
				// has value
				assertData ( onto, pvalUri, uri ( "sio", "SIO_000300") , DataTypeAdapter.printDateTime ( this.dateTime ), XSDVocabulary.DATE_TIME.toString () );
				return;
			}
			
			// Interval, then? 
			// 
			if ( this.lo != null && this.hi != null )
			{
				assertIndividual ( onto, pvalUri, uri ( "sio", "SIO_000944" ) ); // interval
				assertData ( onto, pvalUri, uri ( "biosd-terms", "has-low-value" ), String.valueOf ( this.lo ), XSDVocabulary.DOUBLE.toString () );
				assertData ( onto, pvalUri, uri ( "biosd-terms", "has-high-value" ), String.valueOf ( this.hi ), XSDVocabulary.DOUBLE.toString () );
								
				return;
			}
		}
		
		/**
		 * True if it's has a unit, or a value, or range values, which can be numerical or date values
		 */
		public boolean isNumberOrDate ()
		{
			return 
				this.unitLabel != null || this.value != null || ( this.lo != null && this.hi != null ) 
				|| this.date != null || this.dateTime != null; 
		}
	}
	
	
	public ExpPropValueRdfMapper ()
	{
		super ();
	}

	@Override
	public boolean map ( T sample, ExperimentalPropertyValue pval, Map<String, Object> params )
	{
		try
		{
			// TODO: warnings
			if ( pval == null ) return false;
						
			String sampleAcc = StringUtils.trimToNull ( sample.getAcc () );
			if ( sampleAcc == null ) return false;

			PropValueComponents vcomp = new PropValueComponents ( pval );
			if ( vcomp.valueLabel == null ) return false;
			
			// Process the type
			// 
			ExperimentalPropertyType ptype = pval.getType ();
			
			String typeLabel = BioSdOntologyTermResolver.getExpPropTypeLabel ( ptype );
			if ( typeLabel == null ) return false;
			
			// TODO: is this the same as getAcc() or a secondary accession? 
			// We're ignoring it here, cause the accession is already assigned by the sample mapper.
			if ( "sample accession".equalsIgnoreCase ( typeLabel ) ) return false;

			String typeLabelLC = typeLabel.toLowerCase ();

			// The Sample mapper already deals with these
			if ( "same as".equals ( typeLabel ) 
			     || typeLabelLC.matches ( "derived (from|to)" ) 
			     || typeLabelLC.matches ( "(child|parent) of" ) )
			  return false;
			
			RdfMapperFactory mapFact = this.getMapperFactory ();
			OWLOntology onto = mapFact.getKnowledgeBase ();

			
			// name -> dc:title and similar
			if ( typeLabelLC.matches ( "(sample |group |sample group |)?name" ) )
			{
				assertData ( onto, mapFact.getUri ( sample, params ), uri ( "dc-terms", "title" ), vcomp.valueLabel );
				assertData ( onto, mapFact.getUri ( sample, params ), uri ( "rdfs", "label" ), vcomp.valueLabel );
				return true;
			}

			// 'Sample Description' -> dc-terms:description and similar
			if ( typeLabelLC.matches ( "(sample |group |sample group |)?description" ) )
			{
				assertData ( onto, mapFact.getUri ( sample, params ), uri ( "dc-terms", "description" ), vcomp.valueLabel );
				assertData ( onto, mapFact.getUri ( sample, params ), uri ( "rdfs", "comment" ), vcomp.valueLabel );
				return true;
			}
			
			String valUri = null, typeUri = null;

			// Find a suitable parent accession for properties. If a submission accession is available for this sample,
			// which is passed by either the MSI mapper or the Sample Group mapper, then uses such accession to build the 
			// sample properties URIs, i.e. makes those with common labels unique within the same submission.
			// 
			// In the unlikely case that such MSI accession cannot be recovered here, then use the sample accession itself 
			// as a base for property URIs and that means that each sample will have RDF-wise different properties, even
			// when they share labels. See below for details.
			//
			// TODO: should be correct to share among submissions, to be checked.
			//
			String parentAcc = params == null ? null : (String) params.get ( "msiAccession" );
			if ( parentAcc == null ) parentAcc = sampleAcc;
			parentAcc = urlEncode ( parentAcc );
			
			
			// Define the property value
			String pvalHash = hashUriSignature ( typeLabel + vcomp.valueLabel );
			valUri = uri ( "biosd", "exp-prop-val/" + parentAcc + "#" + pvalHash ); 
			
			// Define its label and possible additional stuff, such as the numerical value, unit, range (see above)
			vcomp.map ( valUri );
			
			
			if ( OLD_MODEL_SUPPORT_FLAG )
				// Define a type URI that is specific to this type and a generic subclass of efo:experimental factor
				typeUri = uri ( "biosd", "exp-prop-type/" + parentAcc + "#" + pvalHash );
			
			// Bottom line: it's an experimental factor
			assertIndividual ( onto, valUri, uri ( "efo", "EFO_0000001" ) );
			
			// And has these labels for the type
			assertData ( onto, valUri, uri ( "dc", "type" ), typeLabel );
			assertAnnotationData ( onto, valUri, uri ( "atlas", "propertyType" ), typeLabel );
			
			
			if ( OLD_MODEL_SUPPORT_FLAG )
			{
				// Another basic fact: it has a type defined as per the original data
				assertLink ( onto, valUri, uri ( "biosd-terms", "has-bio-characteristic-type" ), typeUri );
				assertIndividual ( onto, typeUri, uri ( "efo", "EFO_0000001" ) ); // Experimental factor
				assertAnnotationData ( onto, typeUri, uri ( "rdfs", "label" ), typeLabel );
				assertAnnotationData ( onto, typeUri, uri ( "dc-terms", "title" ), typeLabel );
			}

			// Now, see if the onto discoverer has something more to say
			List<DiscoveredTerm> discoveredTerms = otermResolver.getOntoClassUris ( pval, vcomp.isNumberOrDate () );

			if( !discoveredTerms.isEmpty () )
			{
				for ( DiscoveredTerm dterm: discoveredTerms )
				{
					String discoveredTypeUri = dterm.getIri ();
					assertIndividual ( onto, valUri, discoveredTypeUri );
					
					// Let's track provenance too
					//
					String dtermProv = StringUtils.trimToNull ( dterm.getProvenance () );
					if ( dtermProv != null )
					{
						String annUri = uri ( "biosd", "pvalanntracking#" + hashUriSignature ( valUri + discoveredTypeUri + dtermProv ) );
						
						assertIndividual ( onto, annUri, uri ( "biosd-terms", "SampleAttributeOntologyAnnotation" ) );
						assertData ( onto, annUri, uri ( "dc", "creator"), dtermProv );
						assertLink ( onto, annUri, uri ( "oac", "hasTarget" ), valUri );
						assertLink ( onto, annUri, uri ( "oac", "hasBody" ), discoveredTypeUri );
						
						Double dtermScore = dterm.getScore ();
						if ( dtermScore != null )
							assertData ( 
								onto, 
								annUri, 
								uri ( "biosd-terms", "has-percent-score"), 
								String.valueOf ( dtermScore ), 
								XSDVocabulary.DOUBLE.toString () 
							);
					
						
					  // And now the mappings coming from Bioportal Mapping Service
						// 
						if ( this.fetchOntologyMappings )
						{
							List<OntologyClassMapping> ontoMaps = null;
							BioportalClient bpcli = otermResolver.getOntologyService ();
							synchronized ( bpcli )
							{
								OntologyClass bpClass = bpcli.getOntologyClass ( null, discoveredTypeUri );
								//OntologyClass bpClass = null;
								if ( bpClass != null ) 
									ontoMaps = bpcli.getOntologyClassMappings ( bpClass, BIOPORTAL_ONTOLOGIES, false );
							}
							
							if ( ontoMaps != null ) for ( OntologyClassMapping ontoMap: ontoMaps )
							{
								String targetUri = ontoMap.getTargetClassRef ().getClassIri ();
								if ( discoveredTypeUri.equals ( targetUri ) ) continue;

								String mapSrc = StringUtils.trimToNull ( ontoMap.getSource () );
								String matchUri = "LOOM,UMLS,REST".contains ( mapSrc ) ? "close"
									: "SAME_URI".equals ( mapSrc ) ? null 
									: "related";
								
								if ( matchUri == null ) continue;
	 							
								matchUri = uri ( "skos", matchUri + "Match" );
	
								assertLink ( onto, discoveredTypeUri, matchUri, targetUri );
								
								// The provenance too!
								String provStr = "Bioportal Mapping Service" +  ( mapSrc == null ? "" : " (" + mapSrc + " source)" );
								String mapAnnUri = uri ( 
									"biosd", 
									"mapanntracking#" + hashUriSignature ( discoveredTypeUri + targetUri + provStr ) 
								);
								
								assertIndividual ( onto, mapAnnUri, uri ( "biosd-terms", "OntologyMappingAnnotation" ) );
								assertData ( onto, mapAnnUri, uri ( "dc", "creator"), provStr );
								assertLink ( onto, mapAnnUri, uri ( "oac", "hasTarget" ), discoveredTypeUri );
								assertLink ( onto, mapAnnUri, uri ( "oac", "hasBody" ), targetUri );
								
							} // for each mapping
						} // fetchOntologyMappings flag
					} // dtermProv != null

					
					if ( OLD_MODEL_SUPPORT_FLAG )
					{
						String typeUri1 = uri ( "biosd", "exp-prop-type/ann-based-concept#" + hashUriSignature ( discoveredTypeUri ) );
						assertLink ( onto, valUri, uri ( "biosd-terms", "has-bio-characteristic-type" ), typeUri1 );
						assertIndividual ( onto, typeUri1, discoveredTypeUri );
					}
				}
			}
			
			// Establish how to link the prop value to the sample
			String smpUri = mapFact.getUri ( sample, params );
			if ( pval instanceof BioCharacteristicValue )
				// a direct sub-property of has-sample-attribute
				assertLink ( onto, smpUri, uri ( "biosd-terms", "has-bio-characteristic" ), valUri );				
			else
			{
				if ( OLD_MODEL_SUPPORT_FLAG )
					// This is 'is about' and it's pretty wrong in the case of samples, since it links an information content 
					// entity to an independent continuant. We're keeping it, but it's deprecated now 
					assertLink ( onto, mapFact.getUri ( sample, params ), uri ( "sio", "SIO_000332" ), valUri );
			}
			
			// sub-property of sio:SIO_000008 ('has attribute'), we define it redundantly, as a super-property, grouping 
			// specific cases.
			assertLink ( onto, mapFact.getUri ( sample, params ), uri ( "biosd-terms", "has-sample-attribute" ), valUri );

			// Now we have has-sample-attribute and possibly more specific properties too

			return true;
		} 
		catch ( Exception ex )
		{
			throw new RdfMappingException ( String.format ( 
				"Error while mapping sample '%s'.[%s]=[%s]: '%s'", 
				sample.getAcc (), pval.getType (), pval, ex.getMessage () ), 
			ex );
		}
	}
	
}
