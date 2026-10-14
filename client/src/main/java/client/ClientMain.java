package client;

import chess.ChessGame;
import chess.ChessPiece;

/**
 * Provides the temporary command-line entry point for the chess client.
 */
public class ClientMain {

    public static void main(String[] args) {
        ChessPiece piece = new ChessPiece(
                ChessGame.TeamColor.WHITE,
                ChessPiece.PieceType.PAWN);

        System.out.println("240 Chess Client: " + piece);
    }
}
