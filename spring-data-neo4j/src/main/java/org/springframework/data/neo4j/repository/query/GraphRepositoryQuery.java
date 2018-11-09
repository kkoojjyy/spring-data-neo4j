/*
 * Copyright (c)  [2011-2018] "Pivotal Software, Inc." / "Neo Technology" / "Graph Aware Ltd."
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with
 * separate copyright notices and license terms. Your use of the source
 * code for these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 *
 */

package org.springframework.data.neo4j.repository.query;

import java.util.HashMap;
import java.util.Map;

import org.neo4j.ogm.metadata.MetaData;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.repository.query.spel.ParameterizedQuery;
import org.springframework.data.repository.query.Parameter;
import org.springframework.data.repository.query.Parameters;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.ResultProcessor;

/**
 * Specialisation of {@link RepositoryQuery} that handles mapping to object annotated with <code>&#064;Query</code>.
 *
 * @author Mark Angrish
 * @author Luanne Misquitta
 * @author Jasper Blues
 * @author Mark Paluch
 * @author Nicolas Mervaillie
 * @author Michael J. Simons
 */
public class GraphRepositoryQuery extends AbstractGraphRepositoryQuery {

	private static final Logger LOG = LoggerFactory.getLogger(GraphRepositoryQuery.class);

	private final QueryMethodEvaluationContextProvider evaluationContextProvider;
	private ParameterizedQuery parameterizedQuery;

	GraphRepositoryQuery(GraphQueryMethod graphQueryMethod, MetaData metaData, Session session,
			QueryMethodEvaluationContextProvider evaluationContextProvider
	) {

		super(graphQueryMethod, metaData, session);

		this.evaluationContextProvider = evaluationContextProvider;
	}

	protected Object doExecute(Query query, Object[] parameters) {

		if (LOG.isDebugEnabled()) {
			LOG.debug("Executing query for method {}", queryMethod.getName());
		}

		GraphParameterAccessor accessor = new GraphParametersParameterAccessor(queryMethod, parameters);
		Class<?> returnType = queryMethod.getMethod().getReturnType();

		ResultProcessor processor = queryMethod.getResultProcessor().withDynamicProjection(accessor);

		Object result = getExecution(accessor).execute(query, processor.getReturnedType().getReturnedType());

		return Result.class.equals(returnType) ? result
				: processor.processResult(result, new CustomResultConverter(metaData,
						processor.getReturnedType().getReturnedType(), queryMethod.getMappingContext()));
	}

	protected Query getQuery(Object[] parameters) {
		ParameterizedQuery parameterizedQuery = getParameterizedQuery();
		Map<String, Object> parametersFromQuery = parameterizedQuery.resolveParameter(parameters, this::resolveParams);
		return new Query(parameterizedQuery.getQueryString(), queryMethod.getCountQueryString(), parametersFromQuery);
	}

	private ParameterizedQuery getParameterizedQuery() {
		if (parameterizedQuery == null) {

			Parameters<?, ?> methodParameters = queryMethod.getParameters();
			parameterizedQuery = ParameterizedQuery.getParameterizedQuery(getAnnotationQueryString(), methodParameters,
					evaluationContextProvider);
		}
		return parameterizedQuery;
	}

	Map<String, Object> resolveParams(Parameters<?, ?> methodParameters, Object[] parameters) {

		Map<String, Object> resolvedParameters = new HashMap<>();

		for (Parameter parameter : methodParameters) {
			int parameterIndex = parameter.getIndex();
			Object parameterValue = getParameterValue(parameters[parameterIndex]);

			// We support using parameters based on their index and their name at the same time,
			// so parameters are always bound by index.
			resolvedParameters.put(Integer.toString(parameterIndex), parameterValue);

			// Make sure we don't add "special" parameters as named parameters
			if (parameter.isNamedParameter()) {
				// even though the above check ensures the presence usually, it's probably better to
				// treat #isNamedParameter as a blackbox and not just calling #get() on the optional.
				parameter.getName().ifPresent(parameterName -> resolvedParameters.put(parameterName, parameterValue));
			}
		}

		return resolvedParameters;
	}

	private String getAnnotationQueryString() {
		return getQueryMethod().getQuery();
	}

	private Object getParameterValue(Object parameter) {

		// The parameter might be an entity, try to resolve its id
		Object parameterValue = session.resolveGraphIdFor(parameter);
		if (parameterValue == null) { // Either not an entity or not persisted
			parameterValue = parameter;
		}
		return parameterValue;
	}

	@Override
	protected boolean isCountQuery() {
		return false;
	}

	@Override
	protected boolean isExistsQuery() {
		return false;
	}

	@Override
	protected boolean isDeleteQuery() {
		return false;
	}
}
