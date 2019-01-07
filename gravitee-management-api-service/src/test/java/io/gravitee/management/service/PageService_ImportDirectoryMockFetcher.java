/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.management.service;

import io.gravitee.fetcher.api.Fetcher;
import io.gravitee.fetcher.api.FetcherConfiguration;
import io.gravitee.fetcher.api.FetcherException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com) 
 * @author GraviteeSource Team
 */
public class PageService_ImportDirectoryMockFetcher implements Fetcher{
    public PageService_ImportDirectoryMockFetcher(PageService_MockFetcherConfiguration cfg) {
        super();
    }

    @Override
    public InputStream fetch() throws FetcherException {
        return new ByteArrayInputStream("This is a MOCK".getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String[] files() throws FetcherException {

        return new String[]{
                "/src/doc/m1.md",
                "/swagger.json",
                "/src/doc/sub.m11.md",
                "/src/doc/m2.yaml",
                "/src/folder.with.dot/m2.MD",
                "/src/noFolder",
                "/src/noFolder2/",
                "/src/unsupported.extension"
        };
    }

    @Override
    public FetcherConfiguration getConfiguration() {
        return new PageService_MockFetcherConfiguration();
    }
}
