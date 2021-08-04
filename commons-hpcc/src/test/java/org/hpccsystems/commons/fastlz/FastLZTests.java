/*******************************************************************************
 *     HPCC SYSTEMS software Copyright (C) 2021 HPCC Systems®.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *******************************************************************************/

package org.hpccsystems.commons.fastlz;

import java.util.Arrays;

import org.hpccsystems.commons.fastlz.FastLZ;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(org.hpccsystems.commons.annotations.BaseTests.class)
public class FastLZTests 
{
    @Test
    public void testCompressionDecompression() throws Exception
    {
        // Test small len compression
        {
            byte[] compressed = new byte[shortInput.length];
            int compressedLen = FastLZ.compress(shortInput, compressed);

            byte[] decompressed = new byte[shortInput.length];
            FastLZ.decompress(compressed, compressedLen, decompressed);

            Assert.assertTrue(Arrays.equals(shortInput, decompressed));
        }

        // Test long len compression

        // Generate a long input from the short data to avoid having a huge block of text
        int numShortLens = (65536 / shortInput.length) + 1;
        byte[] longInput = new byte[numShortLens * shortInput.length];
        for (int i = 0; i < numShortLens; i++)
        {
            System.arraycopy(shortInput, 0, longInput, i*shortInput.length, shortInput.length);
        }

        {
            byte[] compressed = new byte[longInput.length];
            int compressedLen = FastLZ.compress(longInput, compressed);

            byte[] decompressed = new byte[longInput.length];
            FastLZ.decompress(compressed, compressedLen, decompressed);

            Assert.assertTrue(Arrays.equals(longInput, decompressed));
        }

        // Test streaming decompression
        {
            byte[] compressed = new byte[longInput.length];
            int compressedLen = FastLZ.compress(longInput, compressed);

            byte[] decompressed = new byte[longInput.length];

            FastLZ.DecompressionState state = new FastLZ.DecompressionState(compressedLen,longInput.length);

            boolean finished = false;
            int availLen = 100;
            while (!finished)
            {
                finished = FastLZ.decompress_streaming(state, compressed, availLen, decompressed);

                if (availLen == longInput.length)
                {
                    break;
                }

                availLen += 100;
                if (availLen > longInput.length)
                {
                    availLen = longInput.length;
                }
            }

            Assert.assertTrue(Arrays.equals(longInput, decompressed));
        }
    }

    byte[] shortInput = ("Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aliquam non magna accumsan,"
                    + " blandit ante sit amet, elementum nisl. Pellentesque quis laoreet tellus, id lobortis mi."
                    + " Pellentesque tellus libero, lobortis quis elementum ultrices, cursus ut urna. Sed eget urna"
                    + " magna. Suspendisse eu quam erat. Etiam iaculis ultricies massa, in auctor enim vehicula sit"
                    + " amet. Proin maximus magna sed pulvinar hendrerit. Nam sed posuere erat. Phasellus in purus"
                    + " sit amet lorem suscipit suscipit sed vel odio. Nam lacinia leo.").getBytes();
}

