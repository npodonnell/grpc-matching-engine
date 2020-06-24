package com.example.grpc;

public class MatchResponse {
    final public long matchedVolume;
    final public long meanPrice;

    public MatchResponse(long matchedVolume, long meanPrice) {
        this.matchedVolume = matchedVolume;
        this.meanPrice = meanPrice;
    }
}
