package dev.lukebemish.immaculate.wrapper;

import dev.lukebemish.forkedtaskexecutor.runner.Task;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;

public abstract class WrapperTask implements Task, Wrapper {
    @Override
    public byte[] run(byte[] bytes) throws Exception {
        try (var is = new DataInputStream(new ByteArrayInputStream(bytes))) {
            var fileName = is.readUTF();
            var text = is.readUTF();
            var result = format(fileName, text);
            return result.getBytes(StandardCharsets.UTF_8);
        }
    }
}
