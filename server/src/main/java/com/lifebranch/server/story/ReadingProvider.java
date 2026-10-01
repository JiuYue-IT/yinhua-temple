package com.lifebranch.server.story;

import com.lifebranch.server.model.Reading;
import com.lifebranch.server.model.WishInput;

@FunctionalInterface
public interface ReadingProvider {
    Reading generate(WishInput input) throws StoryGenerationException, InterruptedException;
}
