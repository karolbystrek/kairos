package pl.karolbystrek.kairos.api.order.application;

import org.junit.jupiter.api.Test;
import pl.karolbystrek.kairos.api.account.application.StaffAccessService;
import pl.karolbystrek.kairos.api.account.application.exception.StaffAccessDeniedException;
import pl.karolbystrek.kairos.api.account.application.model.StaffAccessContext;
import pl.karolbystrek.kairos.api.account.application.model.StaffPrincipal;
import pl.karolbystrek.kairos.api.account.domain.TenantRole;
import pl.karolbystrek.kairos.api.account.domain.assignment.AssignmentRole;
import pl.karolbystrek.kairos.api.order.application.model.OrderView;
import pl.karolbystrek.kairos.api.order.domain.OrderStatus;
import pl.karolbystrek.kairos.api.order.infrastructure.persistence.CustomerOrderRepository;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderQueueRefreshTests {
    @Test
    void sharesOnlyEffectiveScopesAndRechecksEachAccountsAccessBeforeUsingTheSnapshot() {
        var access = mock(StaffAccessService.class);
        var repository = mock(CustomerOrderRepository.class);
        var service = new OrderService(null, repository, access, null, null, null);
        var tenant = UUID.randomUUID();
        var location = UUID.randomUUID();
        var first = new StaffPrincipal(UUID.randomUUID(), tenant, TenantRole.ADMIN);
        var second = new StaffPrincipal(UUID.randomUUID(), tenant, TenantRole.ADMIN);
        var member = new StaffPrincipal(UUID.randomUUID(), tenant, TenantRole.MEMBER);
        var other = new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID(), TenantRole.ADMIN);
        for (var principal : List.of(first, second, other)) {
            when(access.resolve(principal)).thenReturn(new StaffAccessContext(principal.accountId(), principal.tenantId(), TenantRole.ADMIN, null, null));
        }
        when(access.resolve(member)).thenReturn(new StaffAccessContext(member.accountId(), tenant, TenantRole.MEMBER, location, AssignmentRole.MANAGER));
        var snapshots = new HashMap<OrderService.QueueScope, List<OrderView>>();

        service.listOrdersForRefresh(first, null, snapshots);
        service.listOrdersForRefresh(second, null, snapshots);
        service.listOrdersForRefresh(member, null, snapshots);
        service.listOrdersForRefresh(other, null, snapshots);
        verify(repository, times(1)).findAllByLocationTenantIdAndStatusInOrderByCreatedAtDesc(tenant, OrderStatus.activeStatuses());
        verify(repository).findAllByLocationIdAndStatusInOrderByCreatedAtDesc(location, OrderStatus.activeStatuses());
        verify(repository).findAllByLocationTenantIdAndStatusInOrderByCreatedAtDesc(other.tenantId(), OrderStatus.activeStatuses());
        when(access.resolve(second)).thenThrow(new StaffAccessDeniedException("revoked"));
        assertThatThrownBy(() -> service.listOrdersForRefresh(second, null, snapshots)).isInstanceOf(StaffAccessDeniedException.class);
        verify(access, times(2)).resolve(second);
    }
}
