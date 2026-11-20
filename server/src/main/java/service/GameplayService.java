package service;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPosition;
import chess.InvalidMoveException;
import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.GameDAO;
import model.AuthData;
import model.GameData;
import service.exception.BadRequestException;
import service.exception.UnauthorizedException;
import service.request.ConnectGameRequest;
import service.request.LeaveGameRequest;
import service.request.MakeMoveRequest;
import service.request.ResignGameRequest;
import service.result.ConnectGameResult;
import service.result.LeaveGameResult;
import service.result.MakeMoveResult;
import service.result.ResignGameResult;

public class GameplayService {

    private final AuthDAO authDAO;
    private final GameDAO gameDAO;
    private final GameLockManager gameLocks;

    public GameplayService(AuthDAO authDAO, GameDAO gameDAO) {
        this(authDAO, gameDAO, new GameLockManager());
    }

    public GameplayService(AuthDAO authDAO, GameDAO gameDAO, GameLockManager gameLocks) {
        this.authDAO = authDAO;
        this.gameDAO = gameDAO;
        this.gameLocks = gameLocks;
    }

    public ConnectGameResult connect(ConnectGameRequest request)
            throws BadRequestException, UnauthorizedException, DataAccessException {
        if (request == null || request.gameID() == null || request.gameID() <= 0) {
            throw new BadRequestException();
        }

        if (request.authToken() == null || request.authToken().isBlank()) {
            throw new UnauthorizedException();
        }

        AuthData auth = authDAO.getAuth(request.authToken());
        if (auth == null) {
            throw new UnauthorizedException();
        }

        GameData game = gameDAO.getGame(request.gameID());
        if (game == null) {
            throw new BadRequestException();
        }

        ChessGame.TeamColor color = null;
        if (auth.username().equals(game.whiteUsername())) {
            color = ChessGame.TeamColor.WHITE;
        } else if (auth.username().equals(game.blackUsername())) {
            color = ChessGame.TeamColor.BLACK;
        }

        return new ConnectGameResult(auth.username(), game, color);
    }

    public MakeMoveResult makeMove(MakeMoveRequest request)
            throws BadRequestException, UnauthorizedException, DataAccessException, InvalidMoveException {
        if (request == null || request.gameID() == null || request.gameID() <= 0) {
            throw new BadRequestException();
        }

        synchronized (gameLocks.forGame(request.gameID())) {
            ConnectGameResult player = connect(
                    new ConnectGameRequest(request.authToken(), request.gameID()));
            requireCurrentPlayer(player);
            requireMovePositions(request.move());

            GameData storedGame = player.game();
            ChessGame updatedGame = new ChessGame(storedGame.game());
            updatedGame.makeMove(request.move());
            String statusMessage = updateGameStatus(updatedGame, storedGame);
            GameData updatedData = new GameData(
                    storedGame.gameID(), storedGame.whiteUsername(), storedGame.blackUsername(),
                    storedGame.gameName(), updatedGame);
            gameDAO.updateGame(updatedData);

            return new MakeMoveResult(player.username(), updatedData, statusMessage);
        }
    }

    public LeaveGameResult leave(LeaveGameRequest request)
            throws BadRequestException, UnauthorizedException, DataAccessException {
        if (request == null || request.gameID() == null || request.gameID() <= 0) {
            throw new BadRequestException();
        }

        synchronized (gameLocks.forGame(request.gameID())) {
            ConnectGameResult participant = connect(
                    new ConnectGameRequest(request.authToken(), request.gameID()));
            GameData storedGame = participant.game();
            String username = participant.username();
            boolean leavesWhite = username.equals(storedGame.whiteUsername());
            boolean leavesBlack = username.equals(storedGame.blackUsername());

            if (leavesWhite || leavesBlack) {
                gameDAO.updateGame(new GameData(
                        storedGame.gameID(),
                        leavesWhite ? null : storedGame.whiteUsername(),
                        leavesBlack ? null : storedGame.blackUsername(),
                        storedGame.gameName(), storedGame.game()));
            }

            return new LeaveGameResult(username);
        }
    }

    public ResignGameResult resign(ResignGameRequest request)
            throws BadRequestException, UnauthorizedException, DataAccessException, InvalidMoveException {
        if (request == null || request.gameID() == null || request.gameID() <= 0) {
            throw new BadRequestException();
        }

        synchronized (gameLocks.forGame(request.gameID())) {
            ConnectGameResult player = connect(
                    new ConnectGameRequest(request.authToken(), request.gameID()));
            requireActivePlayer(player, "resign");
            GameData storedGame = player.game();
            ChessGame completedGame = new ChessGame(storedGame.game());
            completedGame.endGame();
            gameDAO.updateGame(new GameData(
                    storedGame.gameID(), storedGame.whiteUsername(), storedGame.blackUsername(),
                    storedGame.gameName(), completedGame));

            return new ResignGameResult(player.username());
        }
    }

    private String updateGameStatus(ChessGame game, GameData storedGame) {
        ChessGame.TeamColor nextTeam = game.getTeamTurn();
        String player = nextTeam == ChessGame.TeamColor.WHITE
                ? storedGame.whiteUsername() : storedGame.blackUsername();
        if (player == null) {
            player = nextTeam + " player";
        }

        if (game.isInCheckmate(nextTeam)) {
            game.endGame();
            return player + " is in checkmate. The game is over.";
        }
        if (game.isInStalemate(nextTeam)) {
            game.endGame();
            return "Stalemate: " + player + " has no legal moves. The game is a draw.";
        }
        if (game.isInCheck(nextTeam)) {
            return player + " is in check.";
        }
        return null;
    }

    private void requireCurrentPlayer(ConnectGameResult player) throws InvalidMoveException {
        requireActivePlayer(player, "make moves");
        GameData game = player.game();
        String currentPlayer = game.game().getTeamTurn() == ChessGame.TeamColor.WHITE
                ? game.whiteUsername() : game.blackUsername();
        if (!player.username().equals(currentPlayer)) {
            throw new InvalidMoveException("It is not your turn");
        }
    }

    private void requireActivePlayer(ConnectGameResult player, String action) throws InvalidMoveException {
        if (player.playerColor() == null) {
            throw new InvalidMoveException("Observers cannot " + action);
        }

        if (player.game().game().isGameOver()) {
            throw new InvalidMoveException("The game is over");
        }
    }

    private void requireMovePositions(ChessMove move) throws InvalidMoveException {
        if (move == null || !isOnBoard(move.getStartPosition()) || !isOnBoard(move.getEndPosition())) {
            throw new InvalidMoveException("A move must use squares from a1 to h8");
        }
    }

    private boolean isOnBoard(ChessPosition position) {
        return position != null
                && position.getRow() >= 1 && position.getRow() <= 8
                && position.getColumn() >= 1 && position.getColumn() <= 8;
    }
}
