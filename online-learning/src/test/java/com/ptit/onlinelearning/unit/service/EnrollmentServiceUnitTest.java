package com.ptit.onlinelearning.unit.service;

import com.ptit.onlinelearning.common.type.CourseType;
import com.ptit.onlinelearning.common.type.EnrollmentType;
import com.ptit.onlinelearning.exception.DataNotFoundException;
import com.ptit.onlinelearning.exception.InvalidParamException;
import com.ptit.onlinelearning.model.*;
import com.ptit.onlinelearning.repository.*;
import com.ptit.onlinelearning.request.CreateEnrollment;
import com.ptit.onlinelearning.request.EnrollmentRequest;
import com.ptit.onlinelearning.response.PageableResponse;
import com.ptit.onlinelearning.response.enrollment.EnrollmentCourseGroupResponse;
import com.ptit.onlinelearning.response.enrollment.EnrollmentCourseResponse;
import com.ptit.onlinelearning.service.enrollment.EnrollmentService;
import com.ptit.onlinelearning.unit.config.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class EnrollmentServiceUnitTest extends BaseUnitTest {

    @Autowired private EnrollmentService enrollmentService;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseGroupRepository courseGroupRepository;

    // ── Helpers ──────────────────────────────────────────────────────────────
    private User persistUser(String suffix) {
        User user = User.builder()
                .email("u_" + suffix + "_" + System.nanoTime() + "@test.com")
                .accountName("u_" + suffix)
                .isActive(true).emailVerified(true).build();
        return userRepository.save(user);
    }

    private Course persistFreeCourse(String suffix) {
        Course c = new Course();
        c.setTitle("Free " + suffix);
        c.setCode("F-" + System.nanoTime());
        c.setIsFree(true);
        c.setIsPreOrder(false);
        return courseRepository.save(c);
    }

    private Course persistPaidCourse(String suffix) {
        Course c = new Course();
        c.setTitle("Paid " + suffix);
        c.setCode("P-" + System.nanoTime());
        c.setIsFree(false);
        c.setIsPreOrder(false);
        return courseRepository.save(c);
    }

    private Course persistSubscriptionCourse(String suffix) {
        Course c = new Course();
        c.setTitle("Sub " + suffix);
        c.setCode("S-" + System.nanoTime());
        c.setIsFree(true);
        c.setIsPreOrder(false);
        c.setEnrollmentType(EnrollmentType.SUBSCRIPTION);
        c.setExpiredDays(30);
        return courseRepository.save(c);
    }

    private Enrollment persistEnrollment(User user, Course course) {
        Enrollment e = new Enrollment();
        e.setUser(user); e.setCourse(course);
        return enrollmentRepository.save(e);
    }

    private Enrollment persistGroupEnrollment(User user, Course course, CourseGroup group) {
        Enrollment e = new Enrollment();
        e.setUser(user); e.setCourse(course); e.setCourseGroup(group);
        return enrollmentRepository.save(e);
    }

    // =========================================================
    //  1. createEnrollment(User, EnrollmentRequest)
    // =========================================================

    // TC_EN_01: Lỗi thiếu Validate Null Request
    @Test @Transactional
    void createEnrollment_NullRequest_ShouldThrowInvalidParamException() {
        // Arrange
        User user = persistUser("01");

        // Act & Assert
        // Kỳ vọng ném ra lỗi validate, nhưng Service văng NPE
        assertThrows(InvalidParamException.class,
            () -> enrollmentService.createEnrollment(user, (EnrollmentRequest) null));
    }

    // TC_EN_02: Nhánh courseRepository.findById (Course Not Found)
    @Test @Transactional
    void createEnrollment_CourseNotFound_ThrowsDataNotFoundException() {
        // Arrange
        User user = persistUser("02");
        EnrollmentRequest req = EnrollmentRequest.builder().courseId(-999L).build();

        // Act & Assert
        assertThrows(DataNotFoundException.class,
                () -> enrollmentService.createEnrollment(user, req));
    }

    // TC_EN_03: Nhánh if(!course.getIsFree()) (Course is not free)
    @Test @Transactional
    void createEnrollment_PaidCourse_ThrowsInvalidParamException() {
        // Arrange
        User user = persistUser("03");
        Course paid = persistPaidCourse("03");
        EnrollmentRequest req = EnrollmentRequest.builder().courseId(paid.getId()).build();

        // Act & Assert
        InvalidParamException ex = assertThrows(InvalidParamException.class,
                () -> enrollmentService.createEnrollment(user, req));
        assertTrue(ex.getMessage().contains("not free"));
    }

    // TC_EN_04: Nhánh if(existsByUserIdAndCourseId) (Already Enrolled)
    @Test @Transactional
    void createEnrollment_AlreadyEnrolled_ThrowsInvalidParamException() {
        // Arrange
        User user = persistUser("04");
        Course course = persistFreeCourse("04");
        persistEnrollment(user, course);
        EnrollmentRequest req = EnrollmentRequest.builder().courseId(course.getId()).build();

        // Act & Assert
        InvalidParamException ex = assertThrows(InvalidParamException.class,
                () -> enrollmentService.createEnrollment(user, req));
        assertTrue(ex.getMessage().contains("already been enrolled"));
    }

    // TC_EN_05: Nhánh Happy Path (Free Course, Not Subscription)
    @Test @Transactional
    void createEnrollment_FreeCourse_NotSubscription_Success() {
        // Arrange
        User user = persistUser("05");
        Course course = persistFreeCourse("05");
        EnrollmentRequest req = EnrollmentRequest.builder().courseId(course.getId()).build();
        long before = enrollmentRepository.count();

        // Act
        Enrollment result = enrollmentService.createEnrollment(user, req);

        // Assert
        assertNotNull(result.getId());
        assertNull(result.getEndDate());
        assertNotNull(result.getEnrollmentDate());
        assertEquals(before + 1, enrollmentRepository.count());
    }

    // TC_EN_06: Sub-branch if(course.getEnrollmentType() == SUBSCRIPTION) (Bug JPA Lifecycle)
    @Test @Transactional
    void createEnrollment_FreeCourse_Subscription_Success() {
        // Arrange
        User user = persistUser("06");
        Course course = persistSubscriptionCourse("06");
        EnrollmentRequest req = EnrollmentRequest.builder().courseId(course.getId()).build();

        // Act
        // Service văng NPE ở đây vì getCreatedAt() null -> 
        Enrollment result = enrollmentService.createEnrollment(user, req);

        // Assert
        assertNotNull(result.getId());
        assertNotNull(result.getEndDate());
    }

    // =========================================================
    //  2. createEnrollment(User, Long courseGroupId)
    // =========================================================

    // TC_EN_07: Nhánh courseGroupRepository.findById (Group Not Found)
    @Test @Transactional
    void createEnrollment_Group_NotFound_ThrowsDataNotFoundException() {
        // Arrange
        User user = persistUser("07");

        // Act & Assert
        assertThrows(DataNotFoundException.class,
                () -> enrollmentService.createEnrollment(user, -999L));
    }

    // TC_EN_08: Nhánh if(existsByUserIdAndCourseGroupId) (Group Already Enrolled)
    @Test @Transactional
    void createEnrollment_Group_AlreadyEnrolled_ThrowsInvalidParamException() {
        // Arrange
        User user = persistUser("08");
        Course c = persistFreeCourse("08");
        CourseGroup group = CourseGroup.builder().title("G08").build();
        group.setCourses(List.of(c));
        CourseGroup saved = courseGroupRepository.save(group);
        c.setCourseGroup(saved); courseRepository.save(c);
        persistGroupEnrollment(user, c, saved);

        // Act & Assert
        InvalidParamException ex = assertThrows(InvalidParamException.class,
                () -> enrollmentService.createEnrollment(user, saved.getId()));
        assertTrue(ex.getMessage().contains("already been enrolled"));
    }

    // TC_EN_09: Nhánh if(courseGroup.getCourses() == null || isEmpty) (Group No Courses)
    @Test @Transactional
    void createEnrollment_Group_NoCourses_ThrowsInvalidParamException() {
        // Arrange
        User user = persistUser("09");
        CourseGroup group = CourseGroup.builder().title("G09").build();
        group.setCourses(List.of());
        CourseGroup saved = courseGroupRepository.save(group);

        // Act & Assert
        InvalidParamException ex = assertThrows(InvalidParamException.class,
                () -> enrollmentService.createEnrollment(user, saved.getId()));
        assertTrue(ex.getMessage().contains("no courses"));
    }

    // TC_EN_10: Nhánh if(!isValid) (Group Has Paid Course)
    @Test @Transactional
    void createEnrollment_Group_HasPaidCourse_ThrowsInvalidParamException() {
        // Arrange
        User user = persistUser("10");
        Course free = persistFreeCourse("10F");
        Course paid = persistPaidCourse("10P");
        CourseGroup group = CourseGroup.builder().title("G10").build();
        group.setCourses(List.of(free, paid));
        CourseGroup saved = courseGroupRepository.save(group);
        free.setCourseGroup(saved); paid.setCourseGroup(saved);
        courseRepository.save(free); courseRepository.save(paid);

        // Act & Assert
        InvalidParamException ex = assertThrows(InvalidParamException.class,
                () -> enrollmentService.createEnrollment(user, saved.getId()));
        assertTrue(ex.getMessage().contains("not free"));
    }

    // TC_EN_11: Nhánh Happy Path (Group All Free)
    @Test @Transactional
    void createEnrollment_Group_AllFree_Success() {
        // Arrange
        User user = persistUser("11");
        Course c1 = persistFreeCourse("11A");
        Course c2 = persistFreeCourse("11B");
        CourseGroup group = CourseGroup.builder().title("G11").build();
        group.setCourses(List.of(c1, c2));
        CourseGroup saved = courseGroupRepository.save(group);
        c1.setCourseGroup(saved); c2.setCourseGroup(saved);
        courseRepository.save(c1); courseRepository.save(c2);
        long before = enrollmentRepository.count();

        // Act
        List<Enrollment> results = enrollmentService.createEnrollment(user, saved.getId());

        // Assert
        assertEquals(2, results.size());
        enrollmentRepository.flush();
        assertEquals(before + 2, enrollmentRepository.count());
        results.forEach(e -> assertEquals(saved.getId(), e.getCourseGroup().getId()));
    }

    // TC_EN_12: Sub-branch if(SUBSCRIPTION) trong vòng lặp (Bug JPA Lifecycle)
    @Test @Transactional
    void createEnrollment_Group_SubscriptionCourse_Success() {
        // Arrange
        User user = persistUser("12");
        Course sub = persistSubscriptionCourse("12");
        CourseGroup group = CourseGroup.builder().title("G12").build();
        group.setCourses(List.of(sub));
        CourseGroup saved = courseGroupRepository.save(group);
        sub.setCourseGroup(saved); courseRepository.save(sub);

        // Act
        // Service văng NPE ở đây vì getCreatedAt() null
        List<Enrollment> results = enrollmentService.createEnrollment(user, saved.getId());

        // Assert
        assertFalse(results.isEmpty());
        assertNotNull(results.get(0).getEndDate());
    }

    // =========================================================
    //  3. getEnrollments
    // =========================================================

    // TC_EN_13
    @Test @Transactional
    void getEnrollments_AllNullFilters_ReturnsValidPage() {
        // Arrange
        User user = persistUser("13");
        persistEnrollment(user, persistFreeCourse("13A"));
        persistEnrollment(user, persistFreeCourse("13B"));

        // Act
        Page<Enrollment> page = enrollmentService.getEnrollments(1, 10, "id", "asc", null, null, null);

        // Assert
        assertNotNull(page);
        assertTrue(page.getTotalElements() > 0);
    }

    // TC_EN_14
    @Test @Transactional
    void getEnrollments_SearchEmptyString_NoFilterApplied() {
        // Arrange
        User user = persistUser("14");
        persistEnrollment(user, persistFreeCourse("14"));
        long total = enrollmentRepository.count();

        // Act
        Page<Enrollment> page = enrollmentService.getEnrollments(1, 100, "id", "asc", "", null, null);

        // Assert
        assertEquals(total, page.getTotalElements());
    }

    // TC_EN_15
    @Test @Transactional
    void getEnrollments_SearchNotNull_FilterApplied() {
        // Arrange
        User user = persistUser("15");
        Course match = persistFreeCourse("15Match");
        match.setTitle("UniqueCourse15");
        courseRepository.save(match);
        persistEnrollment(user, match);
        persistEnrollment(user, persistFreeCourse("15NoMatch"));

        // Act
        Page<Enrollment> page = enrollmentService.getEnrollments(1, 100, "id", "asc", "UniqueCourse15", null, null);

        // Assert
        assertTrue(page.getTotalElements() >= 1);
        assertTrue(page.getContent().stream().anyMatch(e -> e.getCourse().getTitle().contains("UniqueCourse15")));
    }

    // TC_EN_16
    @Test @Transactional
    void getEnrollments_InvalidSortOrder_ThrowsIllegalArgumentException() {
        // Arrange
        // (Không cần setup dữ liệu cho exception case này)

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> enrollmentService.getEnrollments(1, 10, "id", "INVALID", null, null, null));
    }

    // =========================================================
    //  4. getEnrollmentById
    // =========================================================

    // TC_EN_17
    @Test @Transactional
    void getEnrollmentById_NotFound_ThrowsDataNotFoundException() {
        // Arrange
        User user = persistUser("17");

        // Act & Assert
        assertThrows(DataNotFoundException.class,
                () -> enrollmentService.getEnrollmentById(-999L, user));
    }

    // TC_EN_18
    @Test @Transactional
    void getEnrollmentById_WrongUser_ThrowsAccessDeniedException() {
        // Arrange
        User owner = persistUser("18a");
        User other = persistUser("18b");
        Course course = persistFreeCourse("18");
        Enrollment saved = persistEnrollment(owner, course);

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> enrollmentService.getEnrollmentById(saved.getId(), other));
    }

    // TC_EN_19
    @Test @Transactional
    void getEnrollmentById_Owner_ReturnsEnrollment() {
        // Arrange
        User user = persistUser("19");
        Course course = persistFreeCourse("19");
        Enrollment saved = persistEnrollment(user, course);

        // Act
        Enrollment result = enrollmentService.getEnrollmentById(saved.getId(), user);

        // Assert
        assertEquals(saved.getId(), result.getId());
    }

    // =========================================================
    //  5. deleteEnrollment
    // =========================================================

    // TC_EN_20: Nhánh ẩn (Lỗi thiếu Validate User Null)
    @Test @Transactional
    void deleteEnrollment_NullUser_ShouldThrowInvalidParamException() {
        // Arrange
        User owner = persistUser("20");
        Course course = persistFreeCourse("20");
        Enrollment saved = persistEnrollment(owner, course);

        // Act & Assert
        // Kỳ vọng InvalidParamException hoặc AccessDenied
        assertThrows(InvalidParamException.class,
            () -> enrollmentService.deleteEnrollment(saved.getId(), null));
    }

    // TC_EN_21: Nhánh enrollmentRepository.findById (Enrollment Not Found)
    @Test @Transactional
    void deleteEnrollment_NotFound_ThrowsDataNotFoundException() {
        // Arrange
        User user = persistUser("21");

        // Act & Assert
        assertThrows(DataNotFoundException.class,
                () -> enrollmentService.deleteEnrollment(-999L, user));
    }

    // TC_EN_22: Nhánh if(!enrollment.getUser().getId().equals(user.getId())) (Access Denied)
    @Test @Transactional
    void deleteEnrollment_WrongUser_ThrowsAccessDeniedException() {
        // Arrange
        User owner = persistUser("22a");
        User other = persistUser("22b");
        Course course = persistFreeCourse("22");
        Enrollment saved = persistEnrollment(owner, course);

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> enrollmentService.deleteEnrollment(saved.getId(), other));
    }

    // TC_EN_23: Nhánh Happy Path (Xóa thành công và flush)
    @Test @Transactional
    void deleteEnrollment_Valid_DeletedFromDB() {
        // Arrange
        User user = persistUser("23");
        Course course = persistFreeCourse("23");
        Enrollment saved = persistEnrollment(user, course);
        long before = enrollmentRepository.count();

        // Act
        enrollmentService.deleteEnrollment(saved.getId(), user);
        enrollmentRepository.flush();

        // Assert
        assertEquals(before - 1, enrollmentRepository.count());
    }

    // =========================================================
    //  6. getAllCoursesEnrolledByUser
    // =========================================================

    // TC_EN_24
    @Test @Transactional
    void getAllCoursesEnrolledByUser_SortAsc_NullSortBy_UsesDefault() {
        // Arrange
        User user = persistUser("24");

        // Act
        PageableResponse<EnrollmentCourseResponse> result =
                enrollmentService.getAllCoursesEnrolledByUser(1, 10, null, "asc", null, user.getId());

        // Assert
        assertNotNull(result);
    }

    // TC_EN_25
    @Test @Transactional
    void getAllCoursesEnrolledByUser_EmptySortBy_UsesDefault() {
        // Arrange
        User user = persistUser("25");

        // Act
        PageableResponse<EnrollmentCourseResponse> result =
                enrollmentService.getAllCoursesEnrolledByUser(1, 10, "  ", "asc", null, user.getId());

        // Assert
        assertNotNull(result);
    }

    // =========================================================
    //  7. getAllCourseGroupsEnrolledByUser
    // =========================================================

    // TC_EN_26
    @Test @Transactional
    void getAllCourseGroupsEnrolledByUser_NoEnrollment_ReturnsEmpty() {
        // Arrange
        User user = persistUser("26");

        // Act
        PageableResponse<EnrollmentCourseResponse> result =
                enrollmentService.getAllCourseGroupsEnrolledByUser(1, 10, null, user.getId());

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
    }

    // =========================================================
    //  8. studentEnroll
    // =========================================================

    // TC_EN_27
    @Test @Transactional
    void studentEnroll_NullList_ShouldThrowInvalidParamException() {
        // Arrange
        // (Không có dữ liệu setup)

        // Act & Assert
        // Kỳ vọng ném InvalidParamException
        assertThrows(InvalidParamException.class,
            () -> enrollmentService.studentEnroll(null));
    }

    // TC_EN_28
    @Test @Transactional
    void studentEnroll_EmptyList_ShouldThrowInvalidParamException() {
        // Arrange
        List<CreateEnrollment> emptyList = List.of();

        // Act & Assert
        // Kỳ vọng ném InvalidParamException, nhưng Service trả về list rỗng
        assertThrows(InvalidParamException.class,
                () -> enrollmentService.studentEnroll(emptyList));
    }

    // TC_EN_29
    @Test @Transactional
    void studentEnroll_ValidList_AllSaved() {
        // Arrange
        User user = persistUser("29");
        Course c1 = persistFreeCourse("29A");
        Course c2 = persistFreeCourse("29B");
        CreateEnrollment i1 = new CreateEnrollment(); i1.setUser(user); i1.setCourse(c1);
        CreateEnrollment i2 = new CreateEnrollment(); i2.setUser(user); i2.setCourse(c2);
        long before = enrollmentRepository.count();

        // Act
        List<Enrollment> results = enrollmentService.studentEnroll(List.of(i1, i2));

        // Assert
        assertEquals(2, results.size());
        enrollmentRepository.flush();
        assertEquals(before + 2, enrollmentRepository.count());
    }

    // =========================================================
    //  9. getEnrollmentCourseGroupDetail
    // =========================================================

    // TC_EN_30
    @Test @Transactional
    void getEnrollmentCourseGroupDetail_NotEnrolled_ThrowsDataNotFoundException() {
        // Arrange
        User user = persistUser("30");
        CourseGroup group = CourseGroup.builder().title("G30").build();
        group.setCourses(List.of());
        CourseGroup saved = courseGroupRepository.save(group);

        // Act & Assert
        assertThrows(DataNotFoundException.class,
                () -> enrollmentService.getEnrollmentCourseGroupDetail(saved.getId(), user.getId()));
    }

    // TC_EN_31
    @Test @Transactional
    void getEnrollmentCourseGroupDetail_Enrolled_ReturnsDetail_FiltersNullCourse() {
        // Arrange
        User user = persistUser("31");
        Course course = persistFreeCourse("31");
        CourseGroup group = CourseGroup.builder().title("G31").build();
        group.setCourses(List.of(course));
        CourseGroup saved = courseGroupRepository.save(group);
        course.setCourseGroup(saved); courseRepository.save(course);
        persistGroupEnrollment(user, course, saved);

        Enrollment nullCourseEnrollment = new Enrollment();
        nullCourseEnrollment.setUser(user); nullCourseEnrollment.setCourseGroup(saved);
        enrollmentRepository.save(nullCourseEnrollment);
        enrollmentRepository.flush();

        // Act
        EnrollmentCourseGroupResponse result =
                enrollmentService.getEnrollmentCourseGroupDetail(saved.getId(), user.getId());

        // Assert
        assertNotNull(result);
        assertEquals(saved.getId(), result.getCourseGroupId());
        assertEquals(1, result.getEnrollmentCourseResponses().size());
    }

    // =========================================================
    //  10. checkEnrollmentCourse
    // =========================================================

    // TC_EN_32
    @Test @Transactional
    void checkEnrollmentCourse_StandaloneEnrolled_ReturnsTrue() {
        // Arrange
        User user = persistUser("32");
        Course course = persistFreeCourse("32");
        Enrollment saved = persistEnrollment(user, course);

        // Act
        Map<String, Object> result = enrollmentService.checkEnrollmentCourse(
                course.getId(), CourseType.STANDALONE, user.getId(), false);

        // Assert
        assertTrue((Boolean) result.get("isEnrolled"));
        assertEquals(saved.getId(), result.get("enrollmentId"));
    }

    // TC_EN_33
    @Test @Transactional
    void checkEnrollmentCourse_StandaloneNotEnrolled_ReturnsFalse() {
        // Arrange
        User user = persistUser("33");
        Course course = persistFreeCourse("33");

        // Act
        Map<String, Object> result = enrollmentService.checkEnrollmentCourse(
                course.getId(), CourseType.STANDALONE, user.getId(), false);

        // Assert
        assertFalse((Boolean) result.get("isEnrolled"));
    }

    // TC_EN_34
    @Test @Transactional
    void checkEnrollmentCourse_StandaloneCheckPreOrder_HasPreOrderInResult() {
        // Arrange
        User user = persistUser("34");
        Course course = persistPaidCourse("34");

        // Act
        Map<String, Object> result = enrollmentService.checkEnrollmentCourse(
                course.getId(), CourseType.STANDALONE, user.getId(), true);
        Map<String, Object> resultNull = enrollmentService.checkEnrollmentCourse(
                course.getId(), CourseType.STANDALONE, user.getId(), null);

        // Assert
        assertTrue(result.containsKey("hasPreOrder"));
        assertFalse(resultNull.containsKey("hasPreOrder"));
    }

    // TC_EN_35
    @Test @Transactional
    void checkEnrollmentCourse_GroupEnrolled_ReturnsTrue() {
        // Arrange
        User user = persistUser("35");
        Course course = persistFreeCourse("35");
        CourseGroup group = CourseGroup.builder().title("G35").build();
        group.setCourses(List.of(course));
        CourseGroup saved = courseGroupRepository.save(group);
        course.setCourseGroup(saved); courseRepository.save(course);
        persistGroupEnrollment(user, course, saved);

        // Act
        Map<String, Object> result = enrollmentService.checkEnrollmentCourse(
                saved.getId(), CourseType.GROUP, user.getId(), false);

        // Assert
        assertTrue((Boolean) result.get("isEnrolled"));
    }

    // TC_EN_36
    @Test @Transactional
    void checkEnrollmentCourse_GroupNotEnrolled_ReturnsFalse() {
        // Arrange
        User user = persistUser("36");
        CourseGroup group = CourseGroup.builder().title("G36").build();
        group.setCourses(List.of());
        CourseGroup saved = courseGroupRepository.save(group);

        // Act
        Map<String, Object> result = enrollmentService.checkEnrollmentCourse(
                saved.getId(), CourseType.GROUP, user.getId(), false);

        // Assert
        assertFalse((Boolean) result.get("isEnrolled"));
    }

    // TC_EN_37
    @Test @Transactional
    void checkEnrollmentCourse_NullCourseType_ElseBranch_ReturnsFalse() {
        // Arrange
        User user = persistUser("37");
        Course course = persistFreeCourse("37");

        // Act
        Map<String, Object> result = enrollmentService.checkEnrollmentCourse(
                course.getId(), null, user.getId(), false);

        // Assert
        assertFalse((Boolean) result.get("isEnrolled"));
    }
}