package service;

import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.UserDAO;
import model.AuthData;
import model.UserData;
import service.exception.AlreadyTakenException;
import service.exception.BadRequestException;
import service.exception.UnauthorizedException;
import service.request.LoginRequest;
import service.request.LogoutRequest;
import service.request.RegisterRequest;
import service.result.LoginResult;
import service.result.LogoutResult;
import service.result.RegisterResult;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Implements account and session operations for chess users.
 */
public class UserService {

    private final UserDAO userDAO;
    private final AuthDAO authDAO;
    private final Supplier<String> authTokenGenerator;

    public UserService(UserDAO userDAO, AuthDAO authDAO) {
        this(userDAO, authDAO, () -> UUID.randomUUID().toString());
    }

    /**
     * Creates a service with a caller-provided authentication token generator.
     */
    UserService(
            UserDAO userDAO,
            AuthDAO authDAO,
            Supplier<String> authTokenGenerator) {
        this.userDAO = userDAO;
        this.authDAO = authDAO;
        this.authTokenGenerator = authTokenGenerator;
    }

    /**
     * Registers a new user and creates the user's first authenticated session.
     */
    public RegisterResult register(RegisterRequest request)
            throws BadRequestException, AlreadyTakenException, DataAccessException {
        validateRegisterRequest(request);

        if (userDAO.getUser(request.username()) != null) {
            throw new AlreadyTakenException();
        }

        UserData userData = new UserData(
                request.username(),
                request.password(),
                request.email());
        userDAO.createUser(userData);

        String authToken = generateUniqueAuthToken();
        authDAO.createAuth(new AuthData(authToken, request.username()));

        return new RegisterResult(request.username(), authToken);
    }

    /**
     * Verifies a user's credentials and creates a new authenticated session.
     */
    public LoginResult login(LoginRequest request)
            throws BadRequestException, UnauthorizedException, DataAccessException {
        validateLoginRequest(request);

        boolean credentialsAreValid = userDAO.verifyCredentials(
                request.username(),
                request.password());
        if (!credentialsAreValid) {
            throw new UnauthorizedException();
        }

        String authToken = generateUniqueAuthToken();
        authDAO.createAuth(new AuthData(authToken, request.username()));

        return new LoginResult(request.username(), authToken);
    }

    /**
     * Ends the authenticated session identified by the request's token.
     */
    public LogoutResult logout(LogoutRequest request)
            throws UnauthorizedException, DataAccessException {
        String authToken = requireAuthToken(request);

        if (authDAO.getAuth(authToken) == null) {
            throw new UnauthorizedException();
        }

        authDAO.deleteAuth(authToken);
        return new LogoutResult();
    }

    private void validateRegisterRequest(RegisterRequest request) throws BadRequestException {
        if (request == null
                || isMissing(request.username())
                || isMissing(request.password())
                || isMissing(request.email())) {
            throw new BadRequestException();
        }
    }

    private void validateLoginRequest(LoginRequest request) throws BadRequestException {
        if (request == null
                || isMissing(request.username())
                || isMissing(request.password())) {
            throw new BadRequestException();
        }
    }

    private String requireAuthToken(LogoutRequest request) throws UnauthorizedException {
        if (request == null || isMissing(request.authToken())) {
            throw new UnauthorizedException();
        }

        return request.authToken();
    }

    private boolean isMissing(String value) {
        return value == null || value.isBlank();
    }

    private String generateUniqueAuthToken() throws DataAccessException {
        String authToken;

        do {
            authToken = authTokenGenerator.get();
        } while (authDAO.getAuth(authToken) != null);

        return authToken;
    }
}
