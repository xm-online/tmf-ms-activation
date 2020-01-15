package com.icthh.xm.tmf.ms.activation.web.rest;

import com.icthh.xm.commons.gen.api.TenantsApiDelegate;
import com.icthh.xm.commons.gen.model.Tenant;
import com.icthh.xm.commons.permission.annotation.PrivilegeDescription;
import com.icthh.xm.tmf.ms.activation.service.tenant.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantResource implements TenantsApiDelegate {

    private final TenantService tenantService;

    @Override
    @PreAuthorize("hasPermission({'tenant':#tenant}, 'ACTIVATION.TENANT.CREATE')")
    @PrivilegeDescription("Privilege to add a new activation tenant")
    public ResponseEntity<Void> addTenant(Tenant tenant) {
        tenantService.createTenant(tenant.getTenantKey());
        return ResponseEntity.ok().build();
    }

    @Override
    @PreAuthorize("hasPermission({'tenantKey':#tenantKey}, 'ACTIVATION.TENANT.DELETE')")
    @PrivilegeDescription("Privilege to delete activation tenant")
    public ResponseEntity<Void> deleteTenant(String tenantKey) {
        tenantService.deleteTenant(tenantKey);
        return ResponseEntity.ok().build();
    }

    @Override
    @PostAuthorize("hasPermission(null, 'ACTIVATION.TENANT.GET_LIST')")
    @PrivilegeDescription("Privilege to get all activation tenants")
    public ResponseEntity<List<Tenant>> getAllTenantInfo() {
        throw new UnsupportedOperationException();
    }

    @Override
    @PostAuthorize("hasPermission({'returnObject': returnObject.body}, 'ACTIVATION.TENANT.GET_LIST.ITEM')")
    @PrivilegeDescription("Privilege to get activation tenant")
    public ResponseEntity<Tenant> getTenant(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    @PreAuthorize("hasPermission({'tenant':#tenant, 'state':#state}, 'ACTIVATION.TENANT.UPDATE')")
    @PrivilegeDescription("Privilege to update activation tenant")
    public ResponseEntity<Void> manageTenant(String tenant, String state) {
        tenantService.manageTenant(tenant, state);
        return ResponseEntity.ok().build();
    }
}
