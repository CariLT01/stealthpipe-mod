package com.stealthpipe;

public class ProofOfWorkChallengeResult {

    public final String token;
    public final int nonce;

    public ProofOfWorkChallengeResult(String token, int nonce) {
        this.token = token;
        this.nonce = nonce;
    }

}
