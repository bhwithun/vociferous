let meta = #{
    name: "work-ticket",
    description: "Implement one open Vociferous GitHub issue and open a pull request",
    when_to_use: "Work a ticket on bhwithun/vociferous. Pass args.issue to choose a number. Otherwise the oldest open issue that has no open pull request is used.",
    phases: [
        #{ title: "Pick", detail: "choose one open issue" },
        #{ title: "Build", detail: "implement that issue and verify it" },
        #{ title: "Publish", detail: "push a branch, open a pull request, comment on the issue" },
    ],
};

let pick_schema = #{
    "type": "object",
    "required": ["skip", "number", "title", "url"],
    "properties": #{
        "skip": #{ "type": "boolean" },
        "number": #{ "type": "integer" },
        "title": #{ "type": "string" },
        "url": #{ "type": "string" },
        "reason": #{ "type": "string" },
    },
};

let requested = if args == () { () } else { args.issue };
let ticket = "";
let title = "";
let url = "";

phase("Pick");
if requested != () {
    ticket = requested.to_string();
    title = "issue " + ticket;
    url = "https://github.com/bhwithun/vociferous/issues/" + ticket;
} else {
    let pick_prompt = "You are picking one GitHub issue to implement. Repository: bhwithun/vociferous. ";
    pick_prompt += "Use search_tool and then the GitHub MCP tools. List open issues, oldest first. ";
    pick_prompt += "Skip an issue when an open pull request already mentions that issue number. ";
    pick_prompt += "Choose the oldest remaining open issue. ";
    pick_prompt += "If none qualify, set skip true, number 0, title empty, url empty, and explain in reason. ";
    pick_prompt += "If one qualifies, set skip false and fill number, title, and url from that issue. ";
    pick_prompt += "Do not print contact names or phone numbers. Do not change any files.";
    let picked = agent(pick_prompt, #{
        label: "pick-issue",
        capability_mode: "read-only",
        output_schema: pick_schema,
    });
    if picked == () || picked.success != true {
        complete(#{ summary: "Could not choose an issue." });
    }
    if type_of(picked.output) == "map" && picked.output.stub == true {
        complete(#{ summary: "Smoke check.", stub: true });
    }
    if picked.output.skip == true {
        let why = "";
        if picked.output.reason != () { why = picked.output.reason; }
        complete(#{ summary: "No open issue to work.", reason: why });
    }
    ticket = picked.output.number.to_string();
    title = picked.output.title;
    url = picked.output.url;
}
log("Working issue " + ticket);

phase("Build");
let build_prompt = "Implement GitHub issue " + ticket + " for the Vociferous Android app. ";
build_prompt += "Repo path: C:\\Users\\brian\\AndroidProjects\\dial-buddy. ";
build_prompt += "GitHub: https://github.com/bhwithun/vociferous/issues/" + ticket + ". ";
build_prompt += "Read the issue with the GitHub MCP tools before editing. ";
build_prompt += "The app is Kotlin and Jetpack Compose, applicationId com.brian.dialbuddy, dark theme only. ";
build_prompt += "Gradle commands run from that directory: .\\gradlew.bat testDebugUnitTest. ";
build_prompt += "If the working tree already has unrelated uncommitted changes, stop and say so. Do not discard them. ";
build_prompt += "Create branch ticket-" + ticket + " from main before the first edit, and make the edits on that branch. ";
build_prompt += "Stay inside the issue. Do not replace the installed dev.emptyvoicemail app. ";
build_prompt += "Do not assign ROLE_DIALER with adb. Do not print contact names or phone numbers in files, logs, or your final answer. ";
build_prompt += "Do not commit secrets, local.properties, keystores, apks, or the recovered directory. ";
build_prompt += "Do not commit. Leave the changes uncommitted on branch ticket-" + ticket + ". ";
build_prompt += "Run the unit tests. For a UI change, install the debug build on the attached phone and check the changed screen with adb screencap, then delete the device png. ";
build_prompt += "Final answer: one short paragraph on what changed and how you verified it. If you could not finish, say what blocked you.";
let built = agent(build_prompt, #{
    label: "build-" + ticket,
    capability_mode: "execute",
});
if built == () || built.success != true {
    complete(#{ summary: "Build did not finish.", issue: ticket });
}
if type_of(built.output) == "map" && built.output.stub == true {
    complete(#{ summary: "Smoke check.", stub: true, issue: ticket });
}

phase("Publish");
let publish_prompt = "Publish the Vociferous work for GitHub issue " + ticket + ". ";
publish_prompt += "Repo path: C:\\Users\\brian\\AndroidProjects\\dial-buddy. Remote: https://github.com/bhwithun/vociferous.git. ";
publish_prompt += "You are on branch ticket-" + ticket + " with uncommitted implementation changes. ";
publish_prompt += "Review git status and git diff. Commit only the files that belong to this issue. ";
publish_prompt += "Never commit local.properties, keystores, apks, google-services.json, or recovered/. ";
publish_prompt += "Do not change git config. Do not force-push. Do not push to main. ";
publish_prompt += "Push ticket-" + ticket + " with upstream set to origin. ";
publish_prompt += "Open a pull request into main with the GitHub MCP create_pull_request tool, owner bhwithun, repo vociferous. ";
publish_prompt += "PR title should reflect the issue. PR body should name the issue as Fixes #" + ticket + " and describe the verification. ";
publish_prompt += "Comment on issue " + ticket + " with the pull request URL, using GitHub MCP add_issue_comment. ";
publish_prompt += "Do not print contact names or phone numbers. ";
publish_prompt += "If the push or the pull request fails, stop and report the exact error. ";
publish_prompt += "Final answer: the pull request URL.";
let published = agent(publish_prompt, #{
    label: "publish-" + ticket,
    capability_mode: "execute",
});
if published == () || published.success != true {
    complete(#{ summary: "Publish did not finish.", issue: ticket, build: built.output });
}
if type_of(published.output) == "map" && published.output.stub == true {
    complete(#{ summary: "Smoke check.", stub: true, issue: ticket });
}
complete(#{
    summary: "Issue " + ticket + " has a pull request.",
    issue: ticket,
    title: title,
    url: url,
    report: published.output,
});
