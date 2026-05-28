package com.ptit.onlinelearning.unit.config;

import com.ptit.onlinelearning.model.Role;
import com.ptit.onlinelearning.model.User;
import com.ptit.onlinelearning.model.UserRole;
import com.ptit.onlinelearning.service.role.IRoleService;
import com.ptit.onlinelearning.service.role.RoleService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Optional;

/**
 * For {@code @Import} from {@code AuthenServiceUnitTest.AssignRoleFailureRegistrationTests} only.
 * Not nested in the test class so Spring Test does not merge it into the default auth test context.
 */
@TestConfiguration
public class AuthAssignRoleFailsConfiguration {

    @Bean
    @Primary
    public IRoleService failingAssignRoleService(RoleService roleService) {
        return new AuthAssignRoleFailsWrapper(roleService);
    }

    static final class AuthAssignRoleFailsWrapper implements IRoleService {

        private final RoleService delegate;

        AuthAssignRoleFailsWrapper(RoleService delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<Role> findRoleByName(String name) {
            return delegate.findRoleByName(name);
        }

        @Override
        public Role createRole(String name) {
            return delegate.createRole(name);
        }

        @Override
        public UserRole assignRoleToUser(User user, Role role) {
            return delegate.assignRoleToUser(user, role);
        }

        @Override
        public UserRole assignDefaultStudentRole(User user) {
            return delegate.assignDefaultStudentRole(user);
        }

        @Override
        public UserRole assignDefaultTeacherRole(User user) {
            return delegate.assignDefaultTeacherRole(user);
        }

        @Override
        public UserRole assignRoleByName(User user, String roleName) {
            throw new RuntimeException("role assign failed");
        }

        @Override
        public List<UserRole> getUserRoles(Long userId) {
            return delegate.getUserRoles(userId);
        }

        @Override
        public boolean userHasRole(Long userId, String roleName) {
            return delegate.userHasRole(userId, roleName);
        }

        @Override
        public void removeRoleFromUser(Long userId, Integer roleId) {
            delegate.removeRoleFromUser(userId, roleId);
        }

        @Override
        public List<Role> getAllRoles() {
            return delegate.getAllRoles();
        }
    }
}
