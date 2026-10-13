package service;

import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.MemoryAuthDAO;
import dataaccess.MemoryUserDAO;
import dataaccess.UserDAO;
import model.AuthData;
import model.UserData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import service.exception.AlreadyTakenException;
import service.exception.BadRequestException;
import service.request.RegisterRequest;
import service.result.RegisterResult;
import testutil.FailingUserDAO;

public class UserServiceTests {

    private UserDAO userDAO;
    private AuthDAO authDAO;
    private UserService userService;

    @BeforeEach
    public void setUp() {
        userDAO = new MemoryUserDAO();
        authDAO = new MemoryAuthDAO();
        userService = new UserService(userDAO, authDAO);
    }

    @Test
    public void registerCreatesUserAndAuthenticatedSession()
            throws BadRequestException, AlreadyTakenException, DataAccessException {
        RegisterRequest request = new RegisterRequest(
                "reader",
                "secret-password",
                "reader@example.com");

        RegisterResult result = userService.register(request);
        UserData storedUser = userDAO.getUser("reader");
        AuthData storedAuth = authDAO.getAuth(result.authToken());

        Assertions.assertEquals("reader", result.username());
        Assertions.assertNotNull(result.authToken());
        Assertions.assertFalse(result.authToken().isBlank());
        Assertions.assertEquals(
                new UserData("reader", "secret-password", "reader@example.com"),
                storedUser);
        Assertions.assertEquals(new AuthData(result.authToken(), "reader"), storedAuth);
    }

    @Test
    public void registerRejectsMissingRequiredFields() {
        RegisterRequest[] incompleteRequests = {
                null,
                new RegisterRequest(null, "secret-password", "reader@example.com"),
                new RegisterRequest("reader", null, "reader@example.com"),
                new RegisterRequest("reader", "secret-password", null)
        };

        for (RegisterRequest request : incompleteRequests) {
            Assertions.assertThrows(
                    BadRequestException.class,
                    () -> userService.register(request));
        }
    }

    @Test
    public void registerRejectsBlankRequiredFields() {
        RegisterRequest[] incompleteRequests = {
                new RegisterRequest("   ", "secret-password", "reader@example.com"),
                new RegisterRequest("reader", "   ", "reader@example.com"),
                new RegisterRequest("reader", "secret-password", "   ")
        };

        for (RegisterRequest request : incompleteRequests) {
            Assertions.assertThrows(
                    BadRequestException.class,
                    () -> userService.register(request));
        }
    }

    @Test
    public void registerRejectsExistingUsername() throws DataAccessException {
        UserData originalUser = new UserData(
                "reader",
                "original-password",
                "original@example.com");
        userDAO.createUser(originalUser);
        RegisterRequest duplicateRequest = new RegisterRequest(
                "reader",
                "replacement-password",
                "replacement@example.com");

        Assertions.assertThrows(
                AlreadyTakenException.class,
                () -> userService.register(duplicateRequest));
        Assertions.assertEquals(originalUser, userDAO.getUser("reader"));
    }

    @Test
    public void registerCreatesUniqueTokensForDifferentUsers()
            throws BadRequestException, AlreadyTakenException, DataAccessException {
        RegisterResult firstResult = userService.register(new RegisterRequest(
                "first-user",
                "first-password",
                "first@example.com"));
        RegisterResult secondResult = userService.register(new RegisterRequest(
                "second-user",
                "second-password",
                "second@example.com"));

        Assertions.assertNotEquals(firstResult.authToken(), secondResult.authToken());
        Assertions.assertNotNull(authDAO.getAuth(firstResult.authToken()));
        Assertions.assertNotNull(authDAO.getAuth(secondResult.authToken()));
    }

    @Test
    public void registerPropagatesDataAccessFailure() {
        UserService failingService = new UserService(
                new FailingUserDAO(),
                new MemoryAuthDAO());
        RegisterRequest request = new RegisterRequest(
                "reader",
                "secret-password",
                "reader@example.com");

        DataAccessException exception = Assertions.assertThrows(
                DataAccessException.class,
                () -> failingService.register(request));

        Assertions.assertEquals("User storage is unavailable", exception.getMessage());
    }
}
