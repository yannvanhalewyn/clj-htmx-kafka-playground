clean:
	rm -rf target

css:
	npx @tailwindcss/cli -i ./tailwind/input.css -o ./resources/public/styles.css --watch

run:
	clj -M:dev

repl:
	clj -M:dev:nrepl

test:
	clj -M:test

uberjar:
	clj -T:build all
