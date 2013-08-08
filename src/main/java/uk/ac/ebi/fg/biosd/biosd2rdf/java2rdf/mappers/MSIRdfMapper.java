package uk.ac.ebi.fg.biosd.biosd2rdf.java2rdf.mappers;

import static uk.ac.ebi.fg.java2rdf.utils.NamespaceUtils.ns;

import java.util.Map;

import org.apache.commons.lang.StringUtils;

import uk.ac.ebi.fg.biosd.model.expgraph.BioSample;
import uk.ac.ebi.fg.biosd.model.organizational.BioSampleGroup;
import uk.ac.ebi.fg.biosd.model.organizational.MSI;
import uk.ac.ebi.fg.core_model.organizational.Contact;
import uk.ac.ebi.fg.core_model.organizational.Organization;
import uk.ac.ebi.fg.core_model.organizational.Publication;
import uk.ac.ebi.fg.java2rdf.mappers.BeanRdfMapper;
import uk.ac.ebi.fg.java2rdf.mappers.ObjRdfMapper;
import uk.ac.ebi.fg.java2rdf.mappers.RdfMapperFactory;
import uk.ac.ebi.fg.java2rdf.mappers.RdfMappingException;
import uk.ac.ebi.fg.java2rdf.mappers.RdfUriGenerator;
import uk.ac.ebi.fg.java2rdf.mappers.properties.CollectionPropRdfMapper;
import uk.ac.ebi.fg.java2rdf.mappers.properties.OwlDatatypePropRdfMapper;
import uk.ac.ebi.fg.java2rdf.mappers.properties.InverseOwlObjPropRdfMapper;
import uk.ac.ebi.fg.java2rdf.mappers.properties.OwlObjPropRdfMapper;

import static uk.ac.ebi.fg.java2rdf.utils.Java2RdfUtils.urlEncode;


/**
 * Maps the submission of a SampleTab file to the BioSD database. 
 *
 * <dl><dt>date</dt><dd>Apr 23, 2013</dd></dl>
 * @author Marco Brandizi
 *
 */
public class MSIRdfMapper extends BeanRdfMapper<MSI>
{
	public MSIRdfMapper ()
	{
		super ( 
			ns ( "biosd", "Submission" ), // TODO: is-a iao:document 
			new RdfUriGenerator<MSI>() 
			{
				@Override
				public String getUri ( MSI msi ) { msi.getPublications ();
					return ns ( "biosd", "msi/" + urlEncode ( msi.getAcc () ) );
				}
		});
		this.addPropertyMapper ( "title", new OwlDatatypePropRdfMapper<MSI, String> ( ns ( "dc-terms", "title" ) ) );
		this.addPropertyMapper ( "description", new OwlDatatypePropRdfMapper<MSI, String> ( ns ( "dc-terms", "description" ) ) );
		
		// TODO: more
		
		this.addPropertyMapper ( "samples", new CollectionPropRdfMapper<MSI, BioSample> ( 
			ns ( "obo", "IAO_0000219" ), new OwlObjPropRdfMapper<MSI, BioSample> () ) // denotes
		);
		
		this.addPropertyMapper ( "sampleGroups", new CollectionPropRdfMapper<MSI, BioSampleGroup> ( 
			ns ( "obo", "IAO_0000219" ), new OwlObjPropRdfMapper<MSI, BioSampleGroup> () ) // denotes
		);
		
		// 'is about' (used in (pub, is-about, msi))
		this.addPropertyMapper ( "publications", new CollectionPropRdfMapper<MSI, Publication> ( 
			ns ( "obo", "IAO_0000136" ), new InverseOwlObjPropRdfMapper<MSI, Publication> () )
		);
		
		// TODO: sub-property of ( (dc-terms:creator union dc-terms:contributor ) and ( schema.org:author union schema.org:contributor ) ) 
		this.addPropertyMapper ( "contacts", new CollectionPropRdfMapper<MSI, Contact> ( 
			ns ( "ebi-terms", "has-knowledgeable-person" ), new OwlObjPropRdfMapper<MSI, Contact> () )
		);

		this.addPropertyMapper ( "organizations", new CollectionPropRdfMapper<MSI, Organization> ( 
			ns ( "ebi-terms", "has-knowledgeable-organization" ), new OwlObjPropRdfMapper<MSI, Organization> () )
		);

	}

	@Override
	@SuppressWarnings ( { "rawtypes", "unchecked" } )
	public boolean map ( MSI msi )
	{
		try
		{
			String msiAcc = StringUtils.trimToNull ( msi.getAcc () );
			if ( msiAcc == null ) return false; 
			
			RdfMapperFactory mapFact = this.getMapperFactory ();
			
			((MSIEquippedRdfUriGenerator<Contact>) mapFact.getRdfUriGenerator ( Contact.class )).setMsiAcc ( msiAcc );
			((MSIEquippedRdfUriGenerator<Organization>) mapFact.getRdfUriGenerator ( Organization.class )).setMsiAcc ( msiAcc );

			return super.map ( msi );
		} 
		catch ( Exception ex ) {
			throw new RdfMappingException ( String.format ( 
				"Error while mapping SampleTab submission[%s]: %s", msi.getAcc(), ex.getMessage () ), ex );
		}
	}
	
}
