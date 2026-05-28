package com.ptit.onlinelearning.unit.service;

import com.ptit.onlinelearning.exception.InvalidParamException;
import com.ptit.onlinelearning.model.*;
import com.ptit.onlinelearning.repository.*;
import com.ptit.onlinelearning.request.CreateLessonProgressRequest;
import com.ptit.onlinelearning.response.ProgressCourseResponse;
import com.ptit.onlinelearning.service.lessonprogress.LessonProgressService;
import com.ptit.onlinelearning.unit.config.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

public class LessonProgressServiceUnitTest extends BaseUnitTest {

    @Autowired private LessonProgressService lessonProgressService;
    @Autowired private LessonProgressRepository lessonProgressRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseModuleRepository courseModuleRepository;
    @Autowired private LessonRepository lessonRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private CourseGroupRepository courseGroupRepository;

    // Helpers

    private User persistUser(String suffix) {
        return userRepository.save(User.builder()
                .email("lp_" + suffix + "_" + System.nanoTime() + "@test.com")
                .accountName("lp_" + suffix)
                .isActive(true).emailVerified(true).build());
    }

    private Course persistCourse(String suffix) {
        Course c = new Course();
        c.setTitle("Course " + suffix);
        c.setCode("LP-" + System.nanoTime());
        c.setIsFree(true);
        c.setIsPreOrder(false);
        return courseRepository.save(c);
    }

    private CourseModule persistModule(Course course, String title) {
        CourseModule m = new CourseModule();
        m.setCourseId(course.getId());
        m.setTitle(title);
        return courseModuleRepository.save(m);
    }

    private Lesson persistLesson(CourseModule module, String title) {
        Lesson l = new Lesson();
        l.setModuleId(module.getId());
        l.setTitle(title);
        l.setContentType("VIDEO");
        return lessonRepository.save(l);
    }

    private Enrollment persistEnrollment(User user, Course course) {
        Enrollment e = new Enrollment();
        e.setUser(user); e.setCourse(course);
        return enrollmentRepository.save(e);
    }

    private LessonProgress persistLessonProgress(Long userId, Long lessonId, Long enrollmentId) {
        return lessonProgressRepository.save(LessonProgress.builder()
                .userId(userId).lessonId(lessonId).enrollmentId(enrollmentId).build());
    }

    private CreateLessonProgressRequest buildRequest(Long lessonId, Long enrollmentId) {
        CreateLessonProgressRequest r = new CreateLessonProgressRequest();
        r.setLessonId(lessonId);
        r.setEnrollmentId(enrollmentId);
        return r;
    }

    // =========================================================
    //  createLessonProgress(Long userId, CreateLessonProgressRequest)
    //  Branch 1: lesson not found   → throw InvalidParamException
    //  Branch 2: user not enrolled  → throw InvalidParamException
    //  Branch 3: progress duplicate → throw InvalidParamException
    //  Branch 4: valid              → save to DB
    // =========================================================

    // TC_LP_01: Nhánh lessonRepository.findById (Lesson Not Found)
    @Test @Transactional
    void createLessonProgress_LessonNotFound_ThrowsInvalidParamException() {
        User user = persistUser("01");
        Course course = persistCourse("01");
        Enrollment enrollment = persistEnrollment(user, course);
        long before = lessonProgressRepository.count();
        InvalidParamException ex = assertThrows(InvalidParamException.class,
                () -> lessonProgressService.createLessonProgress(user.getId(),
                        buildRequest(-999L, enrollment.getId())));
        assertTrue(ex.getMessage().contains("Lesson does not exist"));
        assertEquals(before, lessonProgressRepository.count());
    }

    // TC_LP_02: Nhánh !enrollmentRepository.existsByUserIdAndId (User Not Enrolled)
    @Test @Transactional
    void createLessonProgress_UserNotEnrolled_ThrowsInvalidParamException() {
        User user = persistUser("02");
        User other = persistUser("02b");
        Course course = persistCourse("02");
        CourseModule mod = persistModule(course, "Mod02");
        Lesson lesson = persistLesson(mod, "L02");
        Enrollment enrollment = persistEnrollment(other, course); // enrollment belongs to other
        long before = lessonProgressRepository.count();
        InvalidParamException ex = assertThrows(InvalidParamException.class,
                () -> lessonProgressService.createLessonProgress(user.getId(),
                        buildRequest(lesson.getId(), enrollment.getId())));
        assertTrue(ex.getMessage().contains("not enrolled"));
        assertEquals(before, lessonProgressRepository.count());
    }

    // TC_LP_03: Nhánh lessonProgressRepository.existsBy... (Progress Duplicate)
    @Test @Transactional
    void createLessonProgress_DuplicateProgress_ThrowsInvalidParamException() {
        User user = persistUser("03");
        Course course = persistCourse("03");
        CourseModule mod = persistModule(course, "Mod03");
        Lesson lesson = persistLesson(mod, "L03");
        Enrollment enrollment = persistEnrollment(user, course);
        persistLessonProgress(user.getId(), lesson.getId(), enrollment.getId());
        long before = lessonProgressRepository.count();
        InvalidParamException ex = assertThrows(InvalidParamException.class,
                () -> lessonProgressService.createLessonProgress(user.getId(),
                        buildRequest(lesson.getId(), enrollment.getId())));
        assertTrue(ex.getMessage().contains("already exists"));
        assertEquals(before, lessonProgressRepository.count());
    }

    // TC_LP_04: Nhánh Happy Path (Valid Input -> Saved)
    @Test @Transactional
    void createLessonProgress_ValidInput_SavedToDB() {
        User user = persistUser("04");
        Course course = persistCourse("04");
        CourseModule mod = persistModule(course, "Mod04");
        Lesson lesson = persistLesson(mod, "L04");
        Enrollment enrollment = persistEnrollment(user, course);
        long before = lessonProgressRepository.count();
        assertDoesNotThrow(() -> lessonProgressService.createLessonProgress(user.getId(),
                buildRequest(lesson.getId(), enrollment.getId())));
        assertEquals(before + 1, lessonProgressRepository.count());
        assertTrue(lessonProgressRepository.existsByEnrollmentIdAndLessonIdAndUserId(
                enrollment.getId(), lesson.getId(), user.getId()));
    }

    // TC_LP_11: Nhánh ẩn (Lỗi thiếu Validate Lesson thuộc Course)
    @Test @Transactional
    void createLessonProgress_LessonNotBelongToCourse_ShouldThrowInvalidParamException() {
        // Arrange
        User user = persistUser("11");
        // Course A: User enrolled
        Course courseA = persistCourse("11A");
        Enrollment enrollmentA = persistEnrollment(user, courseA);
        // Course B: User NOT enrolled
        Course courseB = persistCourse("11B");
        CourseModule modB = persistModule(courseB, "Mod11B");
        Lesson lessonB = persistLesson(modB, "L11B");

        // Act & Assert
        // Kỳ vọng: Báo lỗi vì học bài của khóa B nhưng dùng enrollment của khóa A.
        assertThrows(InvalidParamException.class,
                () -> lessonProgressService.createLessonProgress(user.getId(),
                        buildRequest(lessonB.getId(), enrollmentA.getId())),
                "Service ko check Lesson có thuộc về Course của Enrollment hay ko!");
    }

    // TC_LP_12: Nhánh ẩn (Lỗi thiếu Validate Null Request)
    @Test @Transactional
    void createLessonProgress_NullRequest_ShouldThrowInvalidParamException() {
        // Arrange
        User user = persistUser("12");

        // Act & Assert
        // Kỳ vọng ném lỗi validate, nhưng Service ko check null -> văng NullPointerException -> Test FAIL
        assertThrows(InvalidParamException.class,
                () -> lessonProgressService.createLessonProgress(user.getId(), null),
                "Service ko validate null request");
    }

    // =========================================================
    //  calculateUserCourseProgress(Long userId, Long courseId, Long enrollmentId)
    //  Branch 1: totalLessons == null || == 0 → return 0.0
    //  Branch 2: totalLessons > 0 → calculate percentage
    // =========================================================

    // TC_LP_05: Nhánh totalLessons == 0 (Return 0.0)
    @Test @Transactional
    void calculateUserCourseProgress_NoLessons_ReturnsZero() {
        User user = persistUser("05");
        Course course = persistCourse("05");
        Enrollment enrollment = persistEnrollment(user, course);
        Double result = lessonProgressService.calculateUserCourseProgress(
                user.getId(), course.getId(), enrollment.getId());
        assertNotNull(result);
        assertEquals(0.0, result);
    }

    // TC_LP_06: Nhánh Happy Path (Calculate Percentage)
    @Test @Transactional
    void calculateUserCourseProgress_PartialCompletion_ReturnsPercentage() {
        User user = persistUser("06");
        Course course = persistCourse("06");
        CourseModule mod = persistModule(course, "Mod06");
        Lesson l1 = persistLesson(mod, "L06A");
        Lesson l2 = persistLesson(mod, "L06B");
        persistLesson(mod, "L06C");
        persistLesson(mod, "L06D");
        Enrollment enrollment = persistEnrollment(user, course);
        persistLessonProgress(user.getId(), l1.getId(), enrollment.getId());
        persistLessonProgress(user.getId(), l2.getId(), enrollment.getId());
        Double result = lessonProgressService.calculateUserCourseProgress(
                user.getId(), course.getId(), enrollment.getId());
        assertNotNull(result);
        assertEquals(50.00, result, "2/4 lessons = 50.00%");
    }

    // =========================================================
    //  viewUserCourseProgress(Long userId, Long courseId, Long enrollmentId)
    // =========================================================

    // TC_LP_07: Nhánh Happy Path (Returns ProgressCourseResponse)
    @Test @Transactional
    void viewUserCourseProgress_ValidInput_ReturnsCorrectResponse() {
        User user = persistUser("07");
        Course course = persistCourse("07");
        CourseModule mod = persistModule(course, "Mod07");
        Lesson l1 = persistLesson(mod, "L07A");
        persistLesson(mod, "L07B");
        persistLesson(mod, "L07C");
        Enrollment enrollment = persistEnrollment(user, course);
        persistLessonProgress(user.getId(), l1.getId(), enrollment.getId());
        ProgressCourseResponse response = lessonProgressService.viewUserCourseProgress(
                user.getId(), course.getId(), enrollment.getId());
        assertNotNull(response);
        assertEquals(3L, response.getTotalLessons());
        assertEquals(1L, response.getCompletedLessons());
    }

    // =========================================================
    //  caculateUserCourseGroupProgress(Long userId, Long courseGroupId)
    //  Branch 1: listEnrollments.isEmpty() → throw InvalidParamException
    //  Branch 2: enrolled, totalLessonsInGroup == 0 → return 0.0
    //  Branch 3: enrolled, totalLessonsInGroup > 0 → calculate percentage
    // =========================================================

    // TC_LP_08: Nhánh !enrollmentRepository.existsBy...CourseGroupId (Not Enrolled In Group)
    @Test @Transactional
    void caculateUserCourseGroupProgress_NotEnrolled_ThrowsInvalidParamException() {
        User user = persistUser("08");
        CourseGroup group = new CourseGroup();
        group.setTitle("Group08");
        CourseGroup saved = courseGroupRepository.save(group);
        InvalidParamException ex = assertThrows(InvalidParamException.class,
                () -> lessonProgressService.caculateUserCourseGroupProgress(user.getId(), saved.getId()));
        assertTrue(ex.getMessage().contains("not enrolled in the course group"));
    }

    // TC_LP_09: Nhánh totalLessonsInGroup == 0 (Return 0.0)
    @Test @Transactional
    void caculateUserCourseGroupProgress_NoLessonsInGroup_ReturnsZero() {
        User user = persistUser("09");
        Course course = persistCourse("09"); // no lessons
        CourseGroup group = new CourseGroup();
        group.setTitle("Group09");
        CourseGroup saved = courseGroupRepository.save(group);
        courseGroupRepository.flush();
        course.setCourseGroup(saved);          // set FK on owning side
        courseRepository.save(course);
        courseRepository.flush();
        // Create enrollment linking user → course → courseGroup
        Enrollment e = new Enrollment();
        e.setUser(user); e.setCourse(course); e.setCourseGroup(saved);
        enrollmentRepository.save(e);
        enrollmentRepository.flush();
        Double result = lessonProgressService.caculateUserCourseGroupProgress(
                user.getId(), saved.getId());
        assertNotNull(result);
        assertEquals(0.0, result, "Group with no lessons must return 0.0");
    }

    // TC_LP_10: Nhánh Happy Path (Calculate Group Percentage)
    @Test @Transactional
    void caculateUserCourseGroupProgress_PartialCompletion_ReturnsPercentage() {
        User user = persistUser("10");
        Course course = persistCourse("10");
        CourseModule mod = persistModule(course, "Mod10");
        Lesson l1 = persistLesson(mod, "L10A");
        persistLesson(mod, "L10B");
        CourseGroup group = new CourseGroup();
        group.setTitle("Group10");
        CourseGroup saved = courseGroupRepository.save(group);
        courseGroupRepository.flush();
        course.setCourseGroup(saved);          // set FK on owning side
        courseRepository.save(course);
        courseRepository.flush();
        // Create enrollment linking user → course → courseGroup
        Enrollment e = new Enrollment();
        e.setUser(user); e.setCourse(course); e.setCourseGroup(saved);
        Enrollment enrollment = enrollmentRepository.save(e);
        enrollmentRepository.flush();
        // Complete 1 of 2 lessons → expect 50%
        persistLessonProgress(user.getId(), l1.getId(), enrollment.getId());
        Double result = lessonProgressService.caculateUserCourseGroupProgress(
                user.getId(), saved.getId());
        assertNotNull(result);
        assertEquals(50.00, result, "1/2 lessons in group = 50.00%");
    }

    // TC_LP_13: Nhánh ẩn (Lỗi thiếu kiểm tra Course bị Null)
    @Test @Transactional
    void caculateUserCourseGroupProgress_EnrollmentCourseIsNull_ThrowsNullPointerException() {
        // Arrange
        User user = persistUser("13");

        CourseGroup group = new CourseGroup();
        group.setTitle("Group13");
        CourseGroup savedGroup = courseGroupRepository.save(group);
        courseGroupRepository.flush();

        // Tạo Enrollment thuộc Group nhưng ko set Course
        Enrollment e = new Enrollment();
        e.setUser(user);
        e.setCourseGroup(savedGroup);
        // Cố tình ko set course (hoặc course bị xóa logic)
        enrollmentRepository.save(e);
        enrollmentRepository.flush();

        // Act & Assert
        assertThrows(NullPointerException.class,
                () -> lessonProgressService.caculateUserCourseGroupProgress(user.getId(), savedGroup.getId()),
                "Service ko filter các enrollment có course bị null trước khi tính toán");
    }
}