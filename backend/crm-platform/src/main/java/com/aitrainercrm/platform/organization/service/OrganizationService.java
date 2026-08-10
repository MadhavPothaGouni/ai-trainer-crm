package com.aitrainercrm.platform.organization.service;

import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.organization.dto.UpdateOrganizationRequest;
import com.aitrainercrm.platform.organization.entity.Organization;
import com.aitrainercrm.platform.organization.repository.OrganizationRepository;
import com.aitrainercrm.platform.role.service.RoleService;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrganizationRepository organizationRepository;
    private final RoleService roleService;

    /** Creates the organization and its three default roles (OWNER/ADMIN/MEMBER) in one transaction. */
    @Transactional
    public Organization createOrganization(String name) {
        Organization organization = new Organization(name, generateUniqueSlug(name));
        organizationRepository.save(organization);
        roleService.createDefaultRolesForOrganization(organization.getId());
        return organization;
    }

    @Transactional(readOnly = true)
    public Organization getById(UUID organizationId) {
        return organizationRepository.findById(organizationId)
                .filter(org -> !org.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId));
    }

    @Transactional
    public Organization update(UUID organizationId, UpdateOrganizationRequest request) {
        Organization organization = getById(organizationId);
        organization.setName(request.name());
        organization.setDefaultCurrency(request.defaultCurrency());
        organization.setTimezone(request.timezone());
        organization.setFiscalYearStartMonth(request.fiscalYearStartMonth());
        return organizationRepository.save(organization);
    }

    private String generateUniqueSlug(String name) {
        String base = NON_ALNUM.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("-");
        base = base.replaceAll("^-+|-+$", "");
        if (base.isBlank()) {
            base = "organization";
        }

        String candidate = base;
        while (organizationRepository.existsBySlug(candidate)) {
            candidate = base + "-" + (1000 + RANDOM.nextInt(9000));
        }
        return candidate;
    }
}
