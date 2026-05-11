package fr.smp.ptr.foundation.model.parser;

import static org.assertj.core.api.Assertions.assertThat;

import fr.smp.ptr.foundation.model.Blueprint;
import fr.smp.ptr.foundation.model.BoneChannel;
import fr.smp.ptr.foundation.model.LoopMode;
import fr.smp.ptr.foundation.registry.PtrIds;
import org.junit.jupiter.api.Test;

class BlockbenchParserTest {

    @Test
    void parsesBoneTreeAndAnimations() throws Exception {
        Blueprint bp = BlockbenchParser.parseResource(
                "/fixtures/sample.bbmodel", PtrIds.key("model/sample"));

        // 3 bones — body, head (child of body), tail (root)
        assertThat(bp.bones().keySet()).containsExactlyInAnyOrder("body", "head", "tail");
        assertThat(bp.rootBones()).containsExactlyInAnyOrder("body", "tail");

        // Head is parented to body.
        assertThat(bp.bone("head").orElseThrow().parent()).isEqualTo("body");
        assertThat(bp.bone("body").orElseThrow().children()).containsExactly("head");

        // 2 animations: idle (loop), attack (once)
        assertThat(bp.animations().keySet()).containsExactly("idle", "attack");
        assertThat(bp.animation("idle").orElseThrow().loopMode()).isEqualTo(LoopMode.LOOP);
        assertThat(bp.animation("idle").orElseThrow().lengthSeconds()).isEqualTo(2.0);
        assertThat(bp.animation("attack").orElseThrow().loopMode()).isEqualTo(LoopMode.ONCE);
    }

    @Test
    void idleHasThreeRotationKeyframesOnHead() throws Exception {
        Blueprint bp = BlockbenchParser.parseResource(
                "/fixtures/sample.bbmodel", PtrIds.key("model/sample"));
        var idle = bp.animation("idle").orElseThrow();
        var pair = idle.sample("head", BoneChannel.ROTATION, 0.5);
        // At t=0.5 we should be halfway between (0,0,0) and (15,0,0).
        var resolved = pair.resolve();
        assertThat(resolved.x()).isCloseTo(7.5, org.assertj.core.data.Offset.offset(0.001));
    }
}
