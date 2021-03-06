import React from "react";
import {render} from "react-dom";
import {connect} from "react-redux";
import HttpService from "../http/HttpService";
import InlinedSvgs from '../assets/icons/InlinedSvgs'
import PeriodicallyReloadingComponent from './PeriodicallyReloadingComponent'

class HealthCheck extends PeriodicallyReloadingComponent {

  constructor(props) {
    super(props);

    this.state = {
      healthCheck: undefined,
    };
  }

  reload() {
    HttpService.fetchHealthCheck().then(
      (check) => this.setState({ healthCheck: check })
    );
  }

  render() {
    const { healthCheck } = this.state;
    if (!healthCheck || healthCheck.state === 'ok') {
      return null;
    }

    return (
      <div className="healthCheck">
        <div className="icon" title="Warning" dangerouslySetInnerHTML={{__html: InlinedSvgs.tipsWarning}} />
        <span className="errorText">{healthCheck.error || 'State unknown'}</span>
      </div>
    );

  }
}

export default HealthCheck;
