/*
 * Copyright 2021 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.spock.fixtures;

import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.ItemType;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.spock.ReportPortalSpockListener;
import com.epam.reportportal.spock.features.fixtures.SetupFixtureFailedParameters;
import com.epam.reportportal.spock.utils.TestExtension;
import com.epam.reportportal.spock.utils.TestUtils;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.Result;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.reportportal.spock.utils.TestUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

public class TestParametersSetupFixtureFailureIntegrity {
	private final String classId = CommonUtils.namedId("class_");
	private final String methodId = CommonUtils.namedId("method_");
	private final List<String> nestedSteps = Stream.generate(() -> CommonUtils.namedId("method_")).limit(6).collect(Collectors.toList());
	private final List<Pair<String, String>> nestedStepsLink = nestedSteps.stream()
			.map(s -> Pair.of(methodId, s))
			.collect(Collectors.toList());

	private final ReportPortalClient client = mock(ReportPortalClient.class);

	@BeforeEach
	public void setupMock() {
		TestUtils.mockLaunch(client, null, classId, methodId);
		TestUtils.mockNestedSteps(client, nestedStepsLink);
		TestUtils.mockBatchLogging(client);
		TestExtension.listener = new ReportPortalSpockListener(ReportPortal.create(client, standardParameters(), testExecutor()));
	}

	@Test
	public void verify_setup_fixture_failure_correct_reporting_parameterized_feature() {
		Result result = runClasses(SetupFixtureFailedParameters.class);

		assertThat(result.getFailureCount(), equalTo(3));

		verify(client).startLaunch(any());
		verify(client).startTestItem(any(StartTestItemRQ.class));
		verify(client).startTestItem(same(classId), any(StartTestItemRQ.class));
		ArgumentCaptor<StartTestItemRQ> startCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(6)).startTestItem(same(methodId), startCaptor.capture());

		List<StartTestItemRQ> startItems = startCaptor.getAllValues();
		List<String> stepTypes = startItems.stream().map(StartTestItemRQ::getType).collect(Collectors.toList());
		assertThat(stepTypes, containsInAnyOrder(
				ItemType.STEP.name(),
				ItemType.STEP.name(),
				ItemType.STEP.name(),
				ItemType.BEFORE_METHOD.name(),
				ItemType.BEFORE_METHOD.name(),
				ItemType.BEFORE_METHOD.name()
		));
		startItems.forEach(i -> assertThat(i.isHasStats(), equalTo(Boolean.FALSE)));

		ArgumentCaptor<FinishTestItemRQ> finishNestedCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		nestedSteps.forEach(id -> verify(client).finishTestItem(eq(id), finishNestedCaptor.capture()));

		List<FinishTestItemRQ> finishNestedItems = finishNestedCaptor.getAllValues();
		List<String> statuses = finishNestedItems.stream().map(FinishTestItemRQ::getStatus).collect(Collectors.toList());
		assertThat(statuses, containsInAnyOrder(
				ItemStatus.FAILED.name(),
				ItemStatus.FAILED.name(),
				ItemStatus.FAILED.name(),
				ItemStatus.SKIPPED.name(),
				ItemStatus.SKIPPED.name(),
				ItemStatus.SKIPPED.name()
		));
		finishNestedItems.forEach(i -> assertThat(i.getEndTime(), notNullValue()));
		finishNestedItems.stream()
				.filter(i -> ItemStatus.SKIPPED.name().equals(i.getStatus()))
				.forEach(i -> {
					assertThat(i.getIssue(), notNullValue());
					assertThat(i.getIssue().getIssueType(), equalTo(Launch.NOT_ISSUE.getIssueType()));
				});

		ArgumentCaptor<FinishTestItemRQ> finishCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(eq(methodId), finishCaptor.capture());
		assertThat(finishCaptor.getValue().getStatus(), equalTo(ItemStatus.FAILED.name()));

		verify(client).finishTestItem(eq(classId), any());
		//noinspection unchecked
		verify(client, atLeastOnce()).log(any(List.class));
		verifyNoMoreInteractions(client);
	}
}
