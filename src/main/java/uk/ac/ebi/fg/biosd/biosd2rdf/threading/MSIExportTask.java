package uk.ac.ebi.fg.biosd.biosd2rdf.threading;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.apache.commons.lang3.RandomUtils;

import uk.ac.ebi.fg.biosd.biosd2rdf.java2rdf.mapping.BioSdRfMapperFactory;
import uk.ac.ebi.fg.biosd.model.expgraph.BioSample;
import uk.ac.ebi.fg.biosd.model.organizational.MSI;
import uk.ac.ebi.fg.core_model.resources.Resources;

/**
 * Exports a single SampleTab submission, using {@link BioSdRfMapperFactory} and the definitions based on the 
 * Java2RDF framework.
 * 
 * If the submission has many samples, it triggers parallel sample exports, using {@link BioSdExportTask}.
 *
 *
 *
 * <dl><dt>date</dt><dd>19 Jul 2013</dd></dl>
 * @author Marco Brandizi
 *
 */
public class MSIExportTask extends BioSdExportTask
{
	/**
	 * Note that the {@link MSI} is fetched back in {@link #run()}. This is because the task might be executed long after
	 * the initialisation and hence we need a local entity manager.
	 */
	private Long msiId;
	
	/**
	 * A priority between 0 and 7 is assigned to the task, in order to avoid be starved by big queues of 
	 * {@link BioSampleExportTask} generated by other instances of myself.
	 * 
	 * Note that the priority of a submission task ranges betwenn 0 and 8, while sample tasks have 0-10, the idea is that
	 * the submissions never get blocked by they having priority over sample tasks that are waiting to be ran.
	 */
	private MSIExportTask ( BioSdRfMapperFactory rdfMapFactory ) {
		super ( "XPRTMSI:", rdfMapFactory );
		this.setPriority ( RandomUtils.nextInt ( 0, 9 ) );
	}
	
	public MSIExportTask ( BioSdRfMapperFactory rdfMapFactory, BioSdExportService xservice, MSI msi )
	{
		this ( rdfMapFactory );
		if ( msi == null ) 
		{
			this.exitCode = 1;
			throw new IllegalArgumentException ( "Internal error: cannot run an exporter over a null SampleTab submission" );
		}
		this.msiId = msi.getId ();
		this.name += msi.getAcc ();
		this.submitMsiSamples ( xservice, msi );
	}

	public MSIExportTask ( BioSdRfMapperFactory rdfMapFactory, BioSdExportService xservice, Long msiId )
	{
		this ( rdfMapFactory );

		EntityManager em = null;
		
		try 
		{
			em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
			
			@SuppressWarnings ( "serial" )
			MSI msi = em.find ( MSI.class, msiId, new HashMap<String, Object> () { { put ( "org.hibernate.readOnly", true ); } } );
	
			if ( msi == null ) 
			{
				this.exitCode = 1;
				throw new IllegalArgumentException ( "Cannot find SampleTab submission #" + msiId );
			}
			this.msiId = msi.getId ();
			this.name += msi.getAcc ();
			this.submitMsiSamples ( xservice, msi );
		}
		finally {
			if ( em != null && em.isOpen () ) em.close ();
		}
	}

	/**
	 * Submits a SampleTab submission having the parameter accession. Used in the command line form: 'export acc...'
	 */
	public MSIExportTask ( BioSdRfMapperFactory rdfMapFactory, BioSdExportService xservice, String msiAcc )
	{
		this ( rdfMapFactory );

		EntityManager em = null; 
		
		try 
		{
			em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
			
			Query q = em.createQuery ( "FROM MSI WHERE acc = :acc" );
			q.setParameter ( "acc", msiAcc );
			q.setHint("org.hibernate.readOnly", true);
			
			@SuppressWarnings ( "unchecked" )
			List<MSI> msis = (List<MSI>) q.getResultList ();
	
			if ( msis == null || msis.isEmpty () ) 
			{
				this.exitCode = 1;
				throw new IllegalArgumentException ( "Cannot find SampleTab submission #" + msiAcc );
			}
			MSI msi = msis.iterator ().next ();
			this.msiId = msi.getId ();
			this.name += msi.getAcc ();
			this.submitMsiSamples ( xservice, msi );
		}
		finally {
			if ( em != null && em.isOpen () ) em.close ();
		}
	}

	/**
	 * This is invoked by the constructors and, if there are more than 10000 associated to the submission, submit the 
	 * export of such samples as parallel threads, using {@link BioSampleExportTask}. 
	 *  
	 */
	private void submitMsiSamples ( BioSdExportService xservice, MSI msi )
	{
		if ( !msi.isPublic () ) {
			log.trace ( "Skipping non-public submission '{}'", msi.getAcc () );
			return;
		}
		
		// If you have too many samples, let's parallelise it
		// We consider only this case for the moment, cause there are a few submissions with many many samples 
		// (>100k), which takes a lot of time
		Set<BioSample> samples = msi.getSamples ();
		if ( samples.size () > 10000 )
		{
			Map<String, Object> params = new HashMap<String, Object> ();
			params.put ( "msiAccession", msi.getAcc () );
			
			for ( BioSample smp: samples )
				xservice.submit ( new BioSampleExportTask ( this.rdfMapFactory, smp, params ) );
			
			samples.clear ();
		}
	}
	
	
	@Override
	@SuppressWarnings ( "serial" )
	public void run ()
	{
		EntityManager em = null; 
		MSI msi = null;

		try
		{
			em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
			msi = em.find ( 
				MSI.class, 
				msiId, 
				new HashMap<String, Object> () { { put ( "org.hibernate.readOnly", true ); } } 
			);

			rdfMapFactory.map ( msi );
			/* DEBUG log.info ( "Here I should export {}, having a nap instead", msi.getAcc () );
			Thread.sleep ( RandomUtils.nextLong () % 10000 ); */
		} 
		catch ( Throwable ex ) 
		{
			// TODO: proper exit code
			log.error ( "Error while exporting " + msi.getAcc () + ": " + ex.getMessage (), ex );
			exitCode = 1;
		}
		finally {
			if ( em != null && em.isOpen () ) em.close ();
		}
	}
}
