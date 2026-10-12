package pl.karolbystrek.kairos.api.account.api.model;

import org.springframework.data.domain.Page;
import pl.karolbystrek.kairos.api.account.application.model.AccountInvitationView;

import java.util.List;

public record AccountInvitationPageResponse(List<AccountInvitationResponse> items, long totalElements, int page, int size) {
    public static AccountInvitationPageResponse from(Page<AccountInvitationView> page) {
        return new AccountInvitationPageResponse(page.getContent().stream().map(AccountInvitationResponse::from).toList(),
            page.getTotalElements(), page.getNumber(), page.getSize());
    }
}
