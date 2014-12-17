package uk.ac.ebi.fg.biosd.biosd2rdf.utils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.lucene.queryparser.flexible.standard.QueryParserUtil;

import uk.ac.ebi.fgpt.zooma.search.ontodiscover.CachedOntoTermDiscoverer;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntoTermDiscoveryCache;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntologyDiscoveryException;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntologyTermDiscoverer;
import au.com.bytecode.opencsv.CSVReader;

/**
 * An adapter to the special cases we have in BioSD
 * @author brandizi
 *
 */
public class BioSDCachedDiscoverer extends CachedOntoTermDiscoverer
{
	/**
	 * This is loaded from a file, containing a list of property type labels, which of values are known to not being associated to ontology
	 * terms, eg, 'sample id'. 
	 * 
	 */
	private static Set<String> nonOntologyTypes = null;
	
	// Initialise nonOntologyTypes from non_ontology_property_types.csv
	{
		synchronized ( BioSDCachedDiscoverer.class )
		{
			try
			{
				if ( nonOntologyTypes == null )
				{
					nonOntologyTypes = new HashSet<String> ();
					CSVReader csvReader = new CSVReader ( 
						new InputStreamReader ( this.getClass ().getResourceAsStream ( 
							"/non_ontology_property_types.csv" 
					)), '\t' );
					for ( 
						String line[] = csvReader.readNext (); 
						( line = csvReader.readNext () ) != null; 
						nonOntologyTypes.add ( line [ 0 ].trim ().toLowerCase () ) 
					);
					csvReader.close ();
				}
			}
			catch ( IOException ex )
			{
				throw new RuntimeException ( 
					"Internal Error while trying to fetch internal file non_ontology_property_types.csv: " + ex.getMessage (), ex 
				);
			}
		}
	}

	public BioSDCachedDiscoverer ( OntologyTermDiscoverer base, OntoTermDiscoveryCache cache )
		throws IllegalArgumentException
	{
		super ( base, cache );
	}


	public BioSDCachedDiscoverer ( OntologyTermDiscoverer base )
	{
		super ( base );
	}
	

	/**
	 * If typeLabel is in {@link #nonOntologyTypes}, it 
	 */
	@Override
	public List<DiscoveredTerm> getOntologyTermUris ( String valueLabel, String typeLabel ) throws OntologyDiscoveryException
	{
		if ( typeLabel != null && nonOntologyTypes.contains ( typeLabel.trim ().toLowerCase () ) )
		{
			valueLabel = typeLabel;
			typeLabel = null;
		}
		
		if ( valueLabel != null ) valueLabel = QueryParserUtil.escape ( valueLabel );
		if ( typeLabel != null ) typeLabel = QueryParserUtil.escape ( typeLabel );
		
		return super.getOntologyTermUris ( valueLabel, typeLabel );
	}
	
}
