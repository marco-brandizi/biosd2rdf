package uk.ac.ebi.fg.biosd.biosd2rdf.java2rdf.mappers;

import static org.apache.commons.lang.StringUtils.trimToNull;
import static uk.ac.ebi.fg.java2rdf.utils.Java2RdfUtils.hashUriSignature;
import static uk.ac.ebi.fg.java2rdf.utils.NamespaceUtils.ns;
import uk.ac.ebi.fg.core_model.organizational.Organization;
import uk.ac.ebi.fg.java2rdf.mappers.BeanRdfMapper;
import uk.ac.ebi.fg.java2rdf.mappers.properties.OwlDatatypePropRdfMapper;
import static uk.ac.ebi.fg.java2rdf.utils.Java2RdfUtils.urlEncode;

/**
 * TODO: Comment me!
 * 
 * TODO: submission dc-terms:contributor organization
 *
 * <dl><dt>date</dt><dd>Jun 18, 2013</dd></dl>
 * @author Marco Brandizi
 *
 */
public class OrganizationRdfMapper extends BeanRdfMapper<Organization>
{
	public OrganizationRdfMapper ()
	{
		super (
			/* TODO:
			 * ebi-terms:OrganizationReference := 
  		 *   subClassOf ( 'is bearer of' (BFO_0000053) some 'role' (BFO_0000023) )
  		 *   subClassOf schema.org:Organization  dcterms:Agent, foaf:Organization
			 * 
			 */
			ns ( "ebi-terms", "ContactOrganization" ), 
			new MSIEquippedRdfUriGenerator<Organization>()
			{
				@Override public String getUri ( Organization org ) 
				{
					String name = trimToNull ( org.getName () );
					if ( name == null ) return null;
					String id = trimToNull ( org.getEmail () );
					id = id != null ? hashUriSignature ( id ) : urlEncode ( getMsiAcc () ) + "/" + hashUriSignature ( name );    
					
					return ns ( "biosd", "organization/" + id );
			}}
		);
		
		// TODO: schema.org properties, http://schema.rdfs.org/, 
		// also foaf properties

		this.addPropertyMapper ( "email", new OwlDatatypePropRdfMapper<Organization, String> ( ns ( "sch", "email" ) ) );
		this.addPropertyMapper ( "name", new OwlDatatypePropRdfMapper<Organization, String> ( ns ( "sch", "name" ) ) );
		// TODO: dc-terms:description/rdfs:comment
		this.addPropertyMapper ( "description", new OwlDatatypePropRdfMapper<Organization, String> ( ns ( "sch", "description" ) ) );

		// TODO: also dc-terms:title/rdfs:label
		// TODO: not possible for the moment, requires the ability to associate multiple mappers to the bean property: 
		// this.setPropertyMapper ( new OwlDatatypePropRdfMapper<Organization, String> ( "name", ns ( "dc-terms", "title" ) ) );

		// ebi-terms:has-address-line (subprop of dc:description/rdfs:comment)
		// TODO: possibly more annotations to be considered: 
		//   http://answers.semanticweb.com/questions/298/how-can-you-represent-physical-addresses-in-foaf
		this.addPropertyMapper ( "address", new OwlDatatypePropRdfMapper<Organization, String> ( ns ( "ebi-terms", "has-address-line" ) ) );
		
		// TODO: requires a special mapper that goes from string to URIs, without involving bean classes or URI generators
		// this.setPropertyMapper ( new OwlObjPropRdfMapper<Organization, String> ( "URI", ns ( "rdfs", "seeAlso" ) ) );
		
		// TODO: These need to be checked against Zooma
		// o.getRole ();
	}
}
