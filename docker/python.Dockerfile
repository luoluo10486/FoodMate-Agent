FROM python:3.12-slim

WORKDIR /app
COPY agent-runtime/pyproject.toml /app/pyproject.toml
COPY agent-runtime/README.md /app/README.md
COPY agent-runtime/ /app/
RUN pip install --no-cache-dir .

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1
EXPOSE 9000
CMD ["python", "runtime_server.py"]
