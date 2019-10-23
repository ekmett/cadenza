type Name = String
data Expr = Var Name | Lam Name Expr | App Expr Expr  
type Env = [(Name,Value)]
data Value
  = Closure Env Name Expr
  | Neutral Neutral
data Neutral
  = NVar Name
  | NApp Neutral Value


eval :: MonadFail m => Env -> Expr -> m Value
eval e (Var x) = case lookup x e of
  Just v -> return v
  Nothing -> fail "life is hard"
eval e (Lam x b) = Closure e x b
eval e (App f x) = do
  fv <- eval e f
  xv <- eval e x
  apply fv xv

apply :: MonadFail m => Value -> Value -> m Value
apply (Closure e x b) y = eval ((x,y):e) b
apply (Neutral n) v = Neutral (NApp n v)

fresh :: [Name] -> Name -> Name
fresh used v
  | elem v used = fresh used (v ++ "'")
  | otherwise = v

readBack :: MonadFail m => [Name] -> Env -> Value -> Expr
readBack used e (Var v) = Var v
readBack used e (Lam x b) = do
  let x' = fresh used x
  Lam x' (eval ((x,Neutral (NVar x')):b)

nf :: MonadFail m => Env -> Expr -> m Expr
nf e t = do
  v <- eval e t
  readBack [] e t

