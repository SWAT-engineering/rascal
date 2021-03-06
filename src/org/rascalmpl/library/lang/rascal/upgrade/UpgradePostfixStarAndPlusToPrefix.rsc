@bootstrapParser
module lang::rascal::upgrade::UpgradePostfixStarAndPlusToPrefix

import util::FileSystem;
import lang::rascal::\syntax::Rascal;
import ParseTree;
import IO;
import Message;

list[Message] report(loc root) 
   = [*report(parse(#start[Module], m)) | m <- find(root, "rsc")];

void update(loc root) {
  for (m <- find(root, "rsc")) {
    writeFile(m, "<updateTree(parse(#start[Module], m))>");
  }
}

list[Message] report(Tree m) {
  result = [];
  visit(m) {
    case (Pattern) `[<{Pattern ","}* _>,list[<Type elem>] <Name _>,<{Pattern ","}* _>]` : 
      result += [info("found list pattern to upgrade", elem@\loc)];
    case (Pattern) `{<{Pattern ","}* _>,set[<Type elem>] <Name _>,<{Pattern ","}* _>}` : 
      result += [info("found list pattern to upgrade", elem@\loc)];
  }
  
  return result;
}

public Tree updateTree(Tree m) =
  innermost visit(m) {
    case (Pattern) `[<{Pattern ","}* before>,list[<Type elem>] <Name n>,<{Pattern ","}* after>]` =>
         (Pattern) `[<{Pattern ","}* before>, *<Type elem> <Name n>, <{Pattern ","}* after>]`
    case (Pattern) `{<{Pattern ","}* before>,set[<Type elem>] <Name n>,<{Pattern ","}* after>}` =>
         (Pattern) `{<{Pattern ","}* before>, *<Type elem> <Name n>, <{Pattern ","}* after>}`
    case Pattern _ : fail; 
  };
