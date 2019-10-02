{-# LANGUAGE ScopedTypeVariables, RankNTypes #-}

import SimplStg
import Control.Monad.Trans
import CorePrep
import CoreSyn
import CoreToStg
import CostCentre
import DynFlags
import GHC
import GHC.Paths (libdir)
import HscTypes
import Outputable
import qualified StgSyn as GHC
import TyCon

main :: IO ()
main = pp

pp :: IO ()
pp = do
  runGhc (Just libdir) $ do
    _env <- getSession
    dflags <- getSessionDynFlags
    _ <- setSessionDynFlags $ dopt_set (dflags { hscTarget = HscInterpreted }) Opt_D_dump_simpl

    target <- guessTarget "Example.hs" Nothing
    setTargets [target]
    _ <- load LoadAllTargets
    modSum <- getModSummary $ mkModuleName "Example"

    pmod <- parseModule modSum      -- ModuleSummary
    tmod <- typecheckModule pmod    -- TypecheckedSource
    dmod <- desugarModule tmod      -- DesugaredModule
    -- let core = coreModule dmod      -- CoreModule
    -- let cb = mg_binds core -- [CoreBind]
    -- liftIO (putStrLn $ showPpr unsafeGlobalDynFlags cb)
    hsc_env <- GHC.getSession
    let modguts = GHC.dm_core_module dmod
        this_mod = GHC.ms_mod modSum
    (prepd_binds, _) <-
      liftIO
        (CorePrep.corePrepPgm
           hsc_env
           this_mod
           (GHC.ms_location modSum)
           (HscTypes.mg_binds modguts)
           (filter TyCon.isDataTyCon (HscTypes.mg_tcs modguts)))
    -- liftIO (putStrLn $ showPpr unsafeGlobalDynFlags prepd_binds)
    -- dflags <- DynFlags.getDynFlags
    (stg_binds, _) <- liftIO (myCoreToStg dflags this_mod prepd_binds)
    liftIO (liftIO (putStrLn $ showPpr unsafeGlobalDynFlags stg_binds))
    pure ()

-- | Perform core to STG transformation.
myCoreToStg ::
     GHC.DynFlags
  -> GHC.Module
  -> CoreSyn.CoreProgram
  -> IO ([GHC.StgTopBinding], CostCentre.CollectedCCs)
myCoreToStg dflags this_mod prepd_binds = do
  let (stg_binds, cost_centre_info) = CoreToStg.coreToStg dflags this_mod prepd_binds
  stg_binds2 <- SimplStg.stg2stg dflags this_mod stg_binds
  return (stg_binds2, cost_centre_info)

