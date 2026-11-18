-- Pages an answer from a web search relies on, as a JSON array of {title, url}.
ALTER TABLE assistant_messages ADD COLUMN web_links TEXT;
