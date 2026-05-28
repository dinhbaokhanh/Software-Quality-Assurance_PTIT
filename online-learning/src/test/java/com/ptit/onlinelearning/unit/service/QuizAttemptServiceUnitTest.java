package com.ptit.onlinelearning.unit.service;

import com.ptit.onlinelearning.exception.DataNotFoundException;
import com.ptit.onlinelearning.exception.InvalidParamException;
import com.ptit.onlinelearning.model.*;
import com.ptit.onlinelearning.repository.*;
import com.ptit.onlinelearning.request.SubmitAnswerRequest;
import com.ptit.onlinelearning.response.PageableResponse;
import com.ptit.onlinelearning.response.quiz.QuizAttemptResponse;
import com.ptit.onlinelearning.response.quiz.UserAttemptQuizResponse;
import com.ptit.onlinelearning.service.quizattempt.QuizAttemptService;
import com.ptit.onlinelearning.unit.config.BaseUnitTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class QuizAttemptServiceUnitTest extends BaseUnitTest {

    @Autowired private QuizAttemptService quizAttemptService;
    @Autowired private QuizAttemptRepository quizAttemptRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private OptionRepository optionRepository;
    @Autowired private AnswerRepository answerRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseModuleRepository courseModuleRepository;
    @Autowired private InstructorRepository instructorRepository;
    @Autowired private EntityManager entityManager;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private User persistUser(String suffix) {
        return userRepository.save(User.builder()
                .email("qa_" + suffix + "_" + System.nanoTime() + "@test.com")
                .accountName("qa_" + suffix)
                .isActive(true).emailVerified(true).build());
    }

    private Course persistCourse(String suffix) {
        Course c = new Course();
        c.setTitle("Course QA " + suffix);
        c.setCode("QA-" + System.nanoTime());
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

    private Quiz persistQuiz(CourseModule module, String title) {
        return quizRepository.save(Quiz.builder()
                .title(title).courseModule(module).isActive(true).build());
    }

    private Question persistQuestion(Quiz quiz, String text) {
        return questionRepository.save(Question.builder()
                .questionText(text).quiz(quiz).build());
    }

    private Option persistOption(Question question, String text, boolean isCorrect) {
        return optionRepository.save(Option.builder()
                .optionText(text).sortOrder(1L).isCorrect(isCorrect).question(question).build());
    }

    /** Save QuizAttempt then flush+clear so service loads fresh from DB. */
    private QuizAttempt persistQuizAttempt(Quiz quiz, User user) {
        QuizAttempt saved = quizAttemptRepository.save(
                QuizAttempt.builder().quiz(quiz).user(user).build());
        entityManager.flush();
        entityManager.clear();
        return saved;
    }

    private Instructor persistInstructor(String suffix) {
        User u = userRepository.save(User.builder()
                .email("inst_" + suffix + "_" + System.nanoTime() + "@test.com")
                .accountName("inst_" + suffix)
                .isActive(true).emailVerified(true).build());
        return instructorRepository.save(Instructor.builder()
                .userId(u.getId())
                .slug("inst-slug-" + System.nanoTime()).build());
    }

    private Course persistCourseWithInstructor(String suffix, Instructor instructor) {
        Course c = new Course();
        c.setTitle("Course Stats " + suffix);
        c.setCode("STATS-" + System.nanoTime());
        c.setIsFree(true);
        c.setIsPreOrder(false);
        c.setInstructorId(instructor.getId());
        return courseRepository.save(c);
    }

    // =========================================================
    //  1. createQuizAttempt(Long quizId, User user)
    // =========================================================

    // TC_QA_01: Nhánh quizRepository.findById (Quiz Not Found)
    @Test @Transactional
    void createQuizAttempt_QuizNotFound_ThrowsDataNotFoundException() {
        // Arrange
        User user = persistUser("01");
        long before = quizAttemptRepository.count();

        // Act & Assert
        DataNotFoundException ex = assertThrows(DataNotFoundException.class,
                () -> quizAttemptService.createQuizAttempt(-999L, user));
        assertTrue(ex.getMessage().contains("-999"));
        assertEquals(before, quizAttemptRepository.count());
    }

    // TC_QA_02: Nhánh Happy Path (Quiz Attempt Saved)
    @Test @Transactional
    void createQuizAttempt_ValidQuiz_AttemptSavedToDB() {
        // Arrange
        User user = persistUser("02");
        Quiz quiz = persistQuiz(persistModule(persistCourse("02"), "Mod02"), "Quiz02");
        long before = quizAttemptRepository.count();

        // Act
        QuizAttempt result = quizAttemptService.createQuizAttempt(quiz.getId(), user);

        // Assert
        assertNotNull(result.getId());
        assertEquals(quiz.getId(), result.getQuiz().getId());
        assertNull(result.getCorrectAnswers());
        assertNull(result.getCompletedAt());
        assertEquals(before + 1, quizAttemptRepository.count());
    }

    // =========================================================
    //  2. submitQuizAttempt(SubmitAnswerRequest)
    // =========================================================

    // TC_QA_03: Nhánh ẩn (Lỗi thiếu Validate Null Request)
    @Test @Transactional
    void submitQuizAttempt_NullRequest_ThrowsNPE_DueToBug() {
        // Arrange
        // (Không có data)

        // Act & Assert
        // Service gọi request.getQuizAttemptId() mà không check null -> NPE
        assertThrows(NullPointerException.class,
                () -> quizAttemptService.submitQuizAttempt(null),
                "Service không validate null request");
    }

    // TC_QA_04: Nhánh quizAttemptRepository.findById (Attempt Not Found)
    @Test @Transactional
    void submitQuizAttempt_AttemptNotFound_ThrowsDataNotFoundException() {
        // Arrange
        SubmitAnswerRequest req = new SubmitAnswerRequest();
        req.setQuizAttemptId(-999L);
        req.setOptionIds(List.of(1L));

        // Act & Assert
        DataNotFoundException ex = assertThrows(DataNotFoundException.class,
                () -> quizAttemptService.submitQuizAttempt(req));
        assertTrue(ex.getMessage().contains("-999"));
    }

    // TC_QA_05: Nhánh ẩn (Lỗi thiếu Validate Null OptionIds)
    @Test @Transactional
    void submitQuizAttempt_NullOptionIds_ThrowsNPE_DueToBug() {
        // Arrange
        User user = persistUser("05");
        Quiz quiz = persistQuiz(persistModule(persistCourse("05"), "Mod05"), "Quiz05");
        QuizAttempt attempt = persistQuizAttempt(quiz, user);

        SubmitAnswerRequest req = new SubmitAnswerRequest();
        req.setQuizAttemptId(attempt.getId());
        req.setOptionIds(null); 

        // Act & Assert
        // Service gọi optionIds.isEmpty() trên list null -> NPE
        assertThrows(NullPointerException.class,
                () -> quizAttemptService.submitQuizAttempt(req),
                "Service không validate optionIds bị null");
    }

    // TC_QA_06: Nhánh !optionIds.isEmpty() (Empty OptionIds)
    @Test @Transactional
    void submitQuizAttempt_EmptyOptionIds_ThrowsDataNotFoundException() {
        // Arrange
        User user = persistUser("06");
        Quiz quiz = persistQuiz(persistModule(persistCourse("06"), "Mod06"), "Quiz06");
        QuizAttempt attempt = persistQuizAttempt(quiz, user);

        SubmitAnswerRequest req = new SubmitAnswerRequest();
        req.setQuizAttemptId(attempt.getId());
        req.setOptionIds(List.of());

        // Act & Assert
        assertThrows(DataNotFoundException.class,
                () -> quizAttemptService.submitQuizAttempt(req));
        assertNull(quizAttemptRepository.findById(attempt.getId()).orElseThrow().getCompletedAt());
    }

    // TC_QA_07: Nhánh countByIdIn == optionIds.size() (Invalid Option ID)
    @Test @Transactional
    void submitQuizAttempt_InvalidOptionId_ThrowsDataNotFoundException() {
        // Arrange
        User user = persistUser("07");
        Quiz quiz = persistQuiz(persistModule(persistCourse("07"), "Mod07"), "Quiz07");
        QuizAttempt attempt = persistQuizAttempt(quiz, user);

        SubmitAnswerRequest req = new SubmitAnswerRequest();
        req.setQuizAttemptId(attempt.getId());
        req.setOptionIds(List.of(-888L)); // ID ảo

        // Act & Assert
        DataNotFoundException ex = assertThrows(DataNotFoundException.class,
                () -> quizAttemptService.submitQuizAttempt(req));
        assertTrue(ex.getMessage().contains("Option IDs are invalid"));
        assertNull(quizAttemptRepository.findById(attempt.getId()).orElseThrow().getCorrectAnswers());
    }

    // TC_QA_08: Nhánh ẩn (Lỗi thiếu kiểm tra Option thuộc về Quiz)
    @Test @Transactional
    void submitQuizAttempt_OptionsFromDifferentQuiz_ShouldThrowException() {
        // Arrange
        User hacker = persistUser("08");

        // Quiz 1: Quiz mà user đang làm
        Quiz quiz1 = persistQuiz(persistModule(persistCourse("08A"), "Mod08A"), "Quiz08A");
        QuizAttempt attempt = persistQuizAttempt(quiz1, hacker);

        // Quiz 2: Một quiz khác hoàn toàn (User lấy trộm đáp án đúng)
        Quiz quiz2 = persistQuiz(persistModule(persistCourse("08B"), "Mod08B"), "Quiz08B");
        Question q2 = persistQuestion(quiz2, "Câu hỏi của Quiz 2?");
        Option correctOptionQ2 = persistOption(q2, "Đáp án đúng của Quiz 2", true);

        SubmitAnswerRequest req = new SubmitAnswerRequest();
        req.setQuizAttemptId(attempt.getId());
        req.setOptionIds(List.of(correctOptionQ2.getId())); // Submit đáp án của Quiz 2 cho Quiz 1

        // Act & Assert
        // Kỳ vọng: Hệ thống phải phát hiện Option không thuộc Quiz đang làm và chặn lại.
        // Thực tế: Service chỉ check option có tồn tại trong DB -> Cho pass và lưu đáp án -> TEST SẼ FAIL (báo đỏ)
        assertThrows(InvalidParamException.class,
                () -> quizAttemptService.submitQuizAttempt(req),
                "Service không kiểm tra Option có thuộc về Quiz đang làm hay không!");
    }

    // TC_QA_09: Nhánh Happy Path (Answers Saved and Graded)
    @Test @Transactional
    void submitQuizAttempt_ValidInput_AnswersSavedAndResponseReturned() {
        // Arrange
        User user = persistUser("09");
        Quiz quiz = persistQuiz(persistModule(persistCourse("09"), "Mod09"), "Quiz09");
        Question q = persistQuestion(quiz, "Q09?");
        Option correct = persistOption(q, "Correct", true);
        persistOption(q, "Wrong", false); // Option sai

        QuizAttempt attempt = persistQuizAttempt(quiz, user);
        long answersBefore = answerRepository.count();

        SubmitAnswerRequest req = new SubmitAnswerRequest();
        req.setQuizAttemptId(attempt.getId());
        req.setOptionIds(List.of(correct.getId()));

        // Act
        QuizAttemptResponse response = quizAttemptService.submitQuizAttempt(req);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getCorrectAnswer());
        assertNotNull(response.getCompletedAt());
        assertEquals(1, response.getTotalQuestion());
        assertEquals(answersBefore + 1, answerRepository.count());

        QuizAttempt fromDb = quizAttemptRepository.findById(attempt.getId()).orElseThrow();
        assertEquals(1, fromDb.getCorrectAnswers());
        assertNotNull(fromDb.getCompletedAt());
    }

    // =========================================================
    //  3. getQuizAttemptStatisticsByInstructor(User, Pageable)
    // =========================================================

    // TC_QA_10: Nhánh ẩn (Lỗi thiếu Validate Null Instructor)
    @Test @Transactional
    void getQuizAttemptStatisticsByInstructor_NullInstructor_ThrowsNPE_DueToBug() {
        // Arrange
        PageRequest pageable = PageRequest.of(0, 10);

        // Act & Assert
        // Service gọi instructor.getId() mà không check null -> NPE
        assertThrows(NullPointerException.class,
                () -> quizAttemptService.getQuizAttemptStatisticsByInstructor(null, pageable),
                "Service không check null cho tham số instructor");
    }

    // TC_QA_11: Nhánh Happy Path (Returns Statistics)
    @Test @Transactional
    void getQuizAttemptStatisticsByInstructor_WithData_ReturnsValidResponse() {
        // Arrange
        Instructor instructor = persistInstructor("11");

        User instructorUser   = userRepository.findById(instructor.getUserId()).orElseThrow();

        Course course         = persistCourseWithInstructor("11", instructor);
        CourseModule mod      = persistModule(course, "Mod11");
        Quiz quiz             = persistQuiz(mod, "Quiz11");
        persistQuizAttempt(quiz, persistUser("student11"));
        entityManager.flush();
        entityManager.clear();

        // Act
        PageableResponse<UserAttemptQuizResponse> result =
                quizAttemptService.getQuizAttemptStatisticsByInstructor(
                        instructorUser, PageRequest.of(0, 10));

        // Assert
        assertNotNull(result);
        assertTrue(result.getTotalElements() >= 1);
        UserAttemptQuizResponse stats = result.getData().stream()
                .filter(r -> r.getQuizId().equals(quiz.getId()))
                .findFirst().orElse(null);
        assertNotNull(stats);
        assertEquals(1L, stats.getTotalAttempts());
    }
}