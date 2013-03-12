package com.netflix.astyanax.index;

import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;


/**
 * High cardinality index utility.
 *  
 * I broke it off into a read and write interface, if the interface were ever exposed
 * at the layer of the query or batch mutation level.
 * The other major reason for this is that a mutator does not need to be provided 
 * for query.
 * 
 *  
 * 
 * @author marcus
 *
 * @param <C> - the name of the column to be indexed
 * @param <V> - the value to be indexed 
 * @param <K> - the primary/row key of your referencing CF (reverse look up key)
 */
public interface Index<C,V,K> extends IndexRead<C,V,K>, IndexWrite<C,V,K> {

	
			
	/*
	 * Administrative / expensive operations 
	 */
	
	void buildIndex(String targetCF,C name, Class<K> keyType) throws ConnectionException;
	
	void deleteIndex(String targetCF,C name) throws ConnectionException;
	
	
	
}
