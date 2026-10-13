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
import service.request.RegisterRequest;
import service.result.LoginResult;
import service.result.RegisterResult;

import java.util.Objects;
import java.util.UUID;

/**
 * Implements account and session operations for chess users.
 */
public class UserService {

    private final UserDAO userDAO;
    private final AuthDAO authDAO;

    public UserService(UserDAO userDAO, AuthDAO authDAO) {
        this.userDAO = userDAO;
        this.authDAO = authDAO;
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

        UserData userData = userDAO.getUser(request.username());
        boolean passwordMatches = userData != null
                && Objects.equals(userData.password(), request.password());

        if (!passwordMatches) {
            throw new UnauthorizedException();
        }

        String authToken = generateUniqueAuthToken();
        authDAO.createAuth(new AuthData(authToken, request.username()));

        return new LoginResult(request.username(), authToken);
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

    private boolean isMissing(String value) {
        return value == null || value.isBlank();
    }

    private String generateUniqueAuthToken() throws DataAccessException {
        String authToken;

        do {
            authToken = UUID.randomUUID().toString();
        } while (authDAO.getAuth(authToken) != null);

        return authToken;
    }
}
