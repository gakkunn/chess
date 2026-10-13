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
import service.exception.UnauthorizedException;
import service.request.LoginRequest;
import service.request.LogoutRequest;
import service.request.RegisterRequest;
import service.result.LoginResult;
import service.result.LogoutResult;
import service.result.RegisterResult;
import testutil.FailingAuthDAO;
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

    @Test
    public void loginCreatesAuthenticatedSession()
            throws DataAccessException, BadRequestException, UnauthorizedException {
        userDAO.createUser(new UserData(
                "reader",
                "secret-password",
                "reader@example.com"));

        LoginResult result = userService.login(new LoginRequest(
                "reader",
                "secret-password"));
        AuthData storedAuth = authDAO.getAuth(result.authToken());

        Assertions.assertEquals("reader", result.username());
        Assertions.assertNotNull(result.authToken());
        Assertions.assertFalse(result.authToken().isBlank());
        Assertions.assertEquals(new AuthData(result.authToken(), "reader"), storedAuth);
    }

    @Test
    public void loginRejectsMissingRequiredFields() {
        LoginRequest[] incompleteRequests = {
                null,
                new LoginRequest(null, "secret-password"),
                new LoginRequest("reader", null)
        };

        for (LoginRequest request : incompleteRequests) {
            Assertions.assertThrows(
                    BadRequestException.class,
                    () -> userService.login(request));
        }
    }

    @Test
    public void loginRejectsBlankRequiredFields() {
        LoginRequest[] incompleteRequests = {
                new LoginRequest("   ", "secret-password"),
                new LoginRequest("reader", "   ")
        };

        for (LoginRequest request : incompleteRequests) {
            Assertions.assertThrows(
                    BadRequestException.class,
                    () -> userService.login(request));
        }
    }

    @Test
    public void loginRejectsUnknownUsername() {
        LoginRequest request = new LoginRequest("missing-user", "secret-password");

        Assertions.assertThrows(
                UnauthorizedException.class,
                () -> userService.login(request));
    }

    @Test
    public void loginRejectsIncorrectPassword() throws DataAccessException {
        userDAO.createUser(new UserData(
                "reader",
                "secret-password",
                "reader@example.com"));
        LoginRequest request = new LoginRequest("reader", "incorrect-password");

        Assertions.assertThrows(
                UnauthorizedException.class,
                () -> userService.login(request));
    }

    @Test
    public void repeatedLoginsCreateIndependentSessions()
            throws DataAccessException, BadRequestException, UnauthorizedException {
        userDAO.createUser(new UserData(
                "reader",
                "secret-password",
                "reader@example.com"));
        LoginRequest request = new LoginRequest("reader", "secret-password");

        LoginResult firstResult = userService.login(request);
        LoginResult secondResult = userService.login(request);

        Assertions.assertNotEquals(firstResult.authToken(), secondResult.authToken());
        Assertions.assertEquals(
                new AuthData(firstResult.authToken(), "reader"),
                authDAO.getAuth(firstResult.authToken()));
        Assertions.assertEquals(
                new AuthData(secondResult.authToken(), "reader"),
                authDAO.getAuth(secondResult.authToken()));
    }

    @Test
    public void loginPropagatesDataAccessFailure() {
        UserService failingService = new UserService(
                new FailingUserDAO(),
                new MemoryAuthDAO());
        LoginRequest request = new LoginRequest("reader", "secret-password");

        DataAccessException exception = Assertions.assertThrows(
                DataAccessException.class,
                () -> failingService.login(request));

        Assertions.assertEquals("User storage is unavailable", exception.getMessage());
    }

    @Test
    public void logoutRemovesOnlyTheRequestedSession()
            throws DataAccessException, UnauthorizedException {
        AuthData requestedSession = new AuthData("requested-token", "reader");
        AuthData otherSession = new AuthData("other-token", "reader");
        authDAO.createAuth(requestedSession);
        authDAO.createAuth(otherSession);

        LogoutResult result = userService.logout(new LogoutRequest("requested-token"));

        Assertions.assertEquals(new LogoutResult(), result);
        Assertions.assertNull(authDAO.getAuth("requested-token"));
        Assertions.assertEquals(otherSession, authDAO.getAuth("other-token"));
    }

    @Test
    public void logoutRejectsMissingAuthToken() {
        LogoutRequest[] invalidRequests = {
                null,
                new LogoutRequest(null),
                new LogoutRequest("   ")
        };

        for (LogoutRequest request : invalidRequests) {
            Assertions.assertThrows(
                    UnauthorizedException.class,
                    () -> userService.logout(request));
        }
    }

    @Test
    public void logoutRejectsUnknownAuthToken() {
        LogoutRequest request = new LogoutRequest("unknown-token");

        Assertions.assertThrows(
                UnauthorizedException.class,
                () -> userService.logout(request));
    }

    @Test
    public void logoutRejectsAlreadyDeletedSession()
            throws DataAccessException, UnauthorizedException {
        AuthData session = new AuthData("single-use-token", "reader");
        LogoutRequest request = new LogoutRequest(session.authToken());
        authDAO.createAuth(session);

        userService.logout(request);

        Assertions.assertThrows(
                UnauthorizedException.class,
                () -> userService.logout(request));
    }

    @Test
    public void logoutPropagatesDataAccessFailure() {
        UserService failingService = new UserService(
                new MemoryUserDAO(),
                new FailingAuthDAO());
        LogoutRequest request = new LogoutRequest("valid-looking-token");

        DataAccessException exception = Assertions.assertThrows(
                DataAccessException.class,
                () -> failingService.logout(request));

        Assertions.assertEquals("Authentication storage is unavailable", exception.getMessage());
    }
}
