package com.cobaltplatform.api.model.api.response;

import com.cobaltplatform.api.IntegrationTestExecutor;
import com.cobaltplatform.api.model.api.request.CreateAccountRequest;
import com.cobaltplatform.api.model.api.response.AccountApiResponse.AccountApiResponseFactory;
import com.cobaltplatform.api.model.db.Account;
import com.cobaltplatform.api.model.db.AccountSource.AccountSourceId;
import com.cobaltplatform.api.model.db.Institution.InstitutionId;
import com.cobaltplatform.api.service.AccountService;
import com.cobaltplatform.api.util.db.DatabaseProvider;
import com.pyranid.Database;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AccountOnboardingScreeningTreatmentTests {
	@Test
	public void treatmentChangesWithAccountSourceConfiguration() {
		IntegrationTestExecutor.runTransactionallyAndForceRollback(app -> {
			Database database = app.getInjector().getInstance(DatabaseProvider.class).getWritableMasterDatabase();
			AccountService accountService = app.getInjector().getInstance(AccountService.class);
			AccountApiResponseFactory factory = app.getInjector().getInstance(AccountApiResponseFactory.class);
			CreateAccountRequest request = new CreateAccountRequest();
			request.setInstitutionId(InstitutionId.COBALT);
			request.setAccountSourceId(AccountSourceId.ANONYMOUS);
			Account account = accountService.findAccountById(accountService.createAccount(request)).get();

			assertEquals("DEFAULT", factory.create(account).getOnboardingTreatmentId());
			for (String treatment : new String[]{"MODAL", "FUTURE_TREATMENT", "DEFAULT"}) {
				database.execute("UPDATE account_source SET onboarding_treatment_id=? WHERE account_source_id=?",
						treatment, AccountSourceId.ANONYMOUS);
				assertEquals(treatment, factory.create(account).getOnboardingTreatmentId());
				assertEquals("DEFAULT", accountService.findAccountSourceById(AccountSourceId.EMAIL_PASSWORD).get()
						.getOnboardingTreatmentId());
			}
		});
	}
}
